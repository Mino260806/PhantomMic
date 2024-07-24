//
// Created by 双草酸酯 on 2/8/21.
//
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

#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>

#include "logging.h"
#include "native_api.h"
#include "KittyMemory/KittyInclude.hpp"
#include "And64InlineHook/And64InlineHook.hpp"
#include "InlineHook/InlineHook.hpp"
#include "PhantomBridge.h"
#include "hook_compat.h"

extern "C" {
    #include "ffmpeg/libavformat/avformat.h"
    #include "ffmpeg/libavcodec/avcodec.h"
    #include "ffmpeg/libavutil/avutil.h"
    #include "ffmpeg/libavutil/imgutils.h"
    #include "ffmpeg/libavutil/channel_layout.h"
    #include "ffmpeg/libavutil/frame.h"
    #include "ffmpeg/libswresample/swresample.h"
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

int (*log_backup)(int prio, const char* tag, const char* fmt, ...);
int  log_hook(int prio, const char* tag, const char* fmt, ...) {
    va_list args;
    va_start(args, fmt);
    int result = __android_log_vprint(prio, tag, fmt, args);

    if (tag != nullptr && strcmp(tag, "AudioRecord") == 0
            && strstr(fmt, "inputSource %d, sampleRate %u, format %#x, channelMask %#x, frameCount %zu") != nullptr) {
        va_arg(args, char*);
        int inputSource = va_arg(args, int);
        int sampleRate = va_arg(args, int);
        int audioFormat = va_arg(args, int);
        int channelMask = va_arg(args, int);
        int frameCount = va_arg(args, int);

        LOGI("Sample Rate: %d", sampleRate);
        LOGI("Audio Format: %d", audioFormat);
        LOGI("Channel Mask: %d", channelMask);
        LOGI("Frame Count: %d", frameCount);

        JNIEnv* env;
        JVM->AttachCurrentThread(&env, nullptr);
        g_phantomBridge->update_audio_format(env, sampleRate, audioFormat, channelMask);
        g_phantomBridge->load(env);
    }

    va_end(args);

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

    uintptr_t obtainBuffer_symbol = HookCompat::get_obtainBuffer_symbol(g_libTargetELF);
    LOGI("AudioRecord::obtainBuffer at %p", (void*) obtainBuffer_symbol);
    uintptr_t stop_symbol = HookCompat::get_stop_symbol(g_libTargetELF);
    LOGI("AudioRecord::stop at %p", (void*) obtainBuffer_symbol);

    hook_func((void*) obtainBuffer_symbol, (void*) obtainBuffer_hook, (void**) &obtainBuffer_backup);
    hook_func((void*) stop_symbol, (void*) stop_hook, (void**) &stop_backup);
    hook_func((void*) __android_log_print, (void*) log_hook, (void**) &log_backup);
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