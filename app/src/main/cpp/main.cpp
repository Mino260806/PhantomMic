#include <jni.h>
#include <dlfcn.h>
#include <sys/socket.h>
#include <linux/in.h>
#include <arpa/inet.h>
#include <netdb.h>
#include <iomanip>
#include <unwind.h>
#include <sstream>
#include <fstream>
#include <thread>
#include <codecvt>

#include "logging.h"
#include "native_api.h"
#include "KittyMemory/KittyInclude.hpp"

#ifdef __aarch64__
#include "InlineHook/InlineHook.hpp"
#endif

#include "PhantomBridge.h"
#include "hook_compat.h"

struct UnknownArgs {
    char data[1024];
};

jobject j_phantomManager;

JavaVM* JVM;
PhantomBridge* g_phantomBridge;

HookFunType hook_func;
UnhookFunType unhook_func;

int need_log = 5;
size_t acc_frame_count = 0;
int acc_offset = 0;

int32_t (*obtainBuffer_backup)(void*, void*, void*, void*, void*);
int32_t  obtainBuffer_hook(void* v0, void* v1, void* v2, void* v3, void* v4) {
    int32_t status = obtainBuffer_backup(v0, v1, v2, v3, v4);
    size_t frameCount = * (size_t*) v1;
    size_t size = * (size_t*) ((uintptr_t) v1 + sizeof(size_t));
    size_t frameSize = size / frameCount;
    char* raw = * (char**) ((uintptr_t) v1 + sizeof(size_t) * 2);

    if (g_phantomBridge->overwrite_buffer(raw, size) && need_log > 0) {
        LOGI("Overwritten data");
    }

    if (need_log > 0) {
        need_log--;
        LOGI("[%zu] Inside obtainBuffer (%zu x %zu = %zu)", acc_frame_count, frameCount, frameSize, size);
    }

    acc_frame_count += frameCount;
    acc_offset += size;
    return status;
}

void (*stop_backup)(void*);
void  stop_hook(void* thiz) {
    stop_backup(thiz);

    JNIEnv* env;
    JVM->AttachCurrentThread(&env, nullptr);
    LOGI("AudioRecord::stop()");
    g_phantomBridge->unload(env);
}

int32_t (*set_backup)(void* thiz, int32_t inputSource, uint32_t sampleRate, uint32_t format,
                      uint32_t channelMask, size_t frameCount, void* callback_ptr, void* callback_refs,
                      uint32_t notificationFrames, bool threadCanCallJava, int32_t sessionId,
                      int transferType, uint32_t flags, uint32_t uid, int32_t pid, void* pAttributes,
                      int selectedDeviceId, int selectedMicDirection, float microphoneFieldDimension,
                      int32_t maxSharedAudioHistoryMs);
int32_t set_hook(void* thiz, int32_t inputSource, uint32_t sampleRate, uint32_t format,
                 uint32_t channelMask, size_t frameCount, void* callback_ptr, void* callback_refs,
                 uint32_t notificationFrames, bool threadCanCallJava, int32_t sessionId,
                 int transferType, uint32_t flags, uint32_t uid, int32_t pid, void* pAttributes,
                 int selectedDeviceId, int selectedMicDirection, float microphoneFieldDimension,
                 int32_t maxSharedAudioHistoryMs) {

    int32_t result = set_backup(thiz, inputSource, sampleRate, format, channelMask, frameCount,
                                callback_ptr, callback_refs, notificationFrames, threadCanCallJava,
                                sessionId, transferType, flags, uid, pid, pAttributes,
                                selectedDeviceId, selectedMicDirection, microphoneFieldDimension,
                                maxSharedAudioHistoryMs);

    LOGI("AudioRecord::set(...): %d", result);

    JNIEnv* env;
    JVM->AttachCurrentThread(&env, nullptr);
    g_phantomBridge->update_audio_format(env, sampleRate, format, channelMask);
    g_phantomBridge->load(env);

    return result;
}

void on_library_loaded(const char *name, void *handle) {
//    LOGI("Library Loaded %s", name);
}


extern "C" [[gnu::visibility("default")]] [[gnu::used]]
jint JNI_OnLoad(JavaVM *jvm, void*) {
    JNIEnv *env = nullptr;
    jvm->GetEnv((void **)&env, JNI_VERSION_1_6);
    LOGI("JNI_OnLoad");

    JVM = jvm;

    return JNI_VERSION_1_6;
}

extern "C" [[gnu::visibility("default")]] [[gnu::used]]
NativeOnModuleLoaded native_init(const NativeAPIEntries *entries) {
    hook_func = entries->hook_func;
    unhook_func = entries->unhook_func;
    return on_library_loaded;
}

extern "C"
JNIEXPORT void JNICALL
Java_tn_amin_phantom_1mic_PhantomManager_nativeHook(JNIEnv *env, jobject thiz) {
    j_phantomManager = env->NewGlobalRef(thiz);
    g_phantomBridge = new PhantomBridge(j_phantomManager);

    LOGI("Doing c++ hook");

    ElfScanner g_libTargetELF = ElfScanner::createWithPath(HookCompat::get_library_name());

    uintptr_t set_symbol = HookCompat::get_set_symbol(g_libTargetELF);
    LOGI("AudioRecord::set at %p", (void*) set_symbol);
    uintptr_t obtainBuffer_symbol = HookCompat::get_obtainBuffer_symbol(g_libTargetELF);
    LOGI("AudioRecord::obtainBuffer at %p", (void*) obtainBuffer_symbol);
    uintptr_t stop_symbol = HookCompat::get_stop_symbol(g_libTargetELF);
    LOGI("AudioRecord::stop at %p", (void*) obtainBuffer_symbol);

    hook_func((void*) obtainBuffer_symbol, (void*) obtainBuffer_hook, (void**) &obtainBuffer_backup);
    hook_func((void*) stop_symbol, (void*) stop_hook, (void**) &stop_backup);
    hook_func((void*) set_symbol, (void*) set_hook, (void**) &set_backup);
}

extern "C"
JNIEXPORT void JNICALL
Java_tn_amin_phantom_1mic_audio_AudioMaster_onBufferChunkLoaded(JNIEnv *env, jobject thiz,
                                                                jbyteArray buffer_chunk) {
    jbyte* buffer = env->GetByteArrayElements(buffer_chunk, nullptr);
    int size = env->GetArrayLength(buffer_chunk);

    g_phantomBridge->on_buffer_chunk_loaded(buffer, size);

    env->ReleaseByteArrayElements(buffer_chunk, buffer, 0);
}
extern "C"
JNIEXPORT void JNICALL
Java_tn_amin_phantom_1mic_audio_AudioMaster_onLoadDone(JNIEnv *env, jobject thiz) {
    g_phantomBridge->on_load_done();
}