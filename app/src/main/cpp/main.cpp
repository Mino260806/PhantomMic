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

extern "C" {
    #include "ffmpeg/libavformat/avformat.h"
    #include "ffmpeg/libavcodec/avcodec.h"
    #include "ffmpeg/libavutil/avutil.h"
    #include "ffmpeg/libavutil/imgutils.h"
    #include "ffmpeg/libavutil/channel_layout.h"
    #include "ffmpeg/libavutil/frame.h"
    #include "ffmpeg/libswresample/swresample.h"
};

jobject g_phantomManager;

JavaVM* JVM;
JNIEnv *g_env;

HookFunType hook_func;
UnhookFunType unhook_func;

bool need_log = true;
size_t acc_frame_count = 0;

int32_t (*obtainBuffer_backup)(void*, void*, void*, void*, void*);
int32_t  obtainBuffer_hook(void* v0, void* v1, void* v2, void* v3, void* v4) {
    int32_t status = obtainBuffer_backup(v0, v1, v2, v3, v4);
    size_t frameCount = * (size_t*) v1;
    size_t size = * (size_t*) ((uintptr_t) v1 + sizeof(size_t));
    size_t frameSize = size / frameCount;
    void* raw = * (void**) ((uintptr_t) v1 + sizeof(size_t) * 2);

    acc_frame_count += frameCount;

//    memset(raw, 0x7f, size);
//    if (audio_offset + size <= pcm_data_size) {
//        LOGI("Overwriting data");
//        memcpy(raw, pcm_data + audio_offset, size);
//        audio_offset += size;
//    }

    if (need_log) {
        need_log = false;
    }

    LOGI("[%zu] Inside obtainBuffer (%zu x %zu = %zu)", acc_frame_count, frameCount, frameSize, size);
    return status;
}

void set_symbol_hook(user_pt_regs* regs) {
    LOGI("Create engine");
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
    g_env = env;

    g_phantomManager = thiz;

    LOGI("Doing c++ hook");

    ElfScanner g_libTargetELF = ElfScanner::createWithPath("libOpenSLES.so");

    // TODO find function to hook
//    uintptr_t obtainBuffer_symbol = g_libTargetELF.findSymbol("_ZN7android11AudioRecord12obtainBufferEPNS0_6BufferEPK8timespecPS3_Pm");
//    LOGI("obtainBuffer Symbol at %p (%s)", (void*) obtainBuffer_symbol, "_ZN7android11AudioRecord12obtainBufferEPNS0_6BufferEPK8timespecPS3_Pm");
//    uintptr_t create_engine_symbol = g_libTargetELF.findSymbol("slCreateEngine");
//    LOGI("setAudioRecord Symbol at %p (%s)", (void*) create_engine_symbol, "slCreateEngine");
//    uintptr_t start_symbol = g_libTargetELF.findSymbol("_ZN7android11AudioRecord5startENS_11AudioSystem12sync_event_tE15audio_session_t");
//    LOGI("createAudioRecord at %p (%s)", (void*) start_symbol, "_ZN7android11AudioRecord5startENS_11AudioSystem12sync_event_tE15audio_session_t");

//    hook_func((void*) obtainBuffer_symbol, (void*) obtainBuffer_hook, (void**) &obtainBuffer_backup);
//    ModifyIBored(create_engine_symbol, set_symbol_hook);
//        A64HookFunction((void*) set_symbol, (void*) createRecord_hook, (void**) &createRecord_backup);
//        ModifyIBored(set_symbol, modifyIBored);
//        ModifyIBored(start_symbol, modifyIBored2);
//        ModifyIBored((uintptr_t) check_service_symbol, modifyIBored3);
}
