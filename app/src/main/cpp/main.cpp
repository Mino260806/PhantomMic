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

bool need_log = true;
size_t acc_frame_count = 0;
int acc_offset = 0;
JNIEnv* obtainBuffer_env = nullptr;

int32_t (*obtainBuffer_backup)(void*, void*, void*, void*, void*);
int32_t  obtainBuffer_hook(void* v0, void* v1, void* v2, void* v3, void* v4) {
    int32_t status = obtainBuffer_backup(v0, v1, v2, v3, v4);
    size_t frameCount = * (size_t*) v1;
    size_t size = * (size_t*) ((uintptr_t) v1 + sizeof(size_t));
    size_t frameSize = size / frameCount;
    void* raw = * (void**) ((uintptr_t) v1 + sizeof(size_t) * 2);

//    if (obtainBuffer_env == nullptr) {
//    }
    JVM->AttachCurrentThread(&obtainBuffer_env, nullptr);
    jbyte* buffer = g_phantomBridge->get_buffer(obtainBuffer_env, acc_offset, size);

//    memset(raw, 0x7f, size);
    if (buffer != nullptr) {
        LOGI("Overwriting data");
        memcpy(raw, buffer+acc_offset, size);

        g_phantomBridge->release_buffer(obtainBuffer_env, buffer);
    }

    LOGI("[%zu] Inside obtainBuffer (%zu x %zu = %zu)", acc_frame_count, frameCount, frameSize, size);

    acc_frame_count += frameCount;
    acc_offset += size;
    return status;
}

int (*log_backup)(int prio, const char* tag, const char* fmt, ...);
int  log_hook(int prio, const char* tag, const char* fmt, ...) {
//    if (strstr(format, "inputSource %d, sampleRate %u, format %#x, channelMask %#x, frameCount %zu") != nullptr) {
//        unhook_func((void*) __android_log_print);
//
//        LOGI("Found out target");
//
//        hook_func((void*) __android_log_print, (void*) vsnprintf_hook, (void**) &vsnprintf_backup);
//    }
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

//
//void __android_log_print_hook(user_pt_regs* regs) {
//    if (regs->regs[1] != 0) {
//        if (strcmp(reinterpret_cast<const char *>(regs->regs[1]), LOG_TAG) == 0) {
//            return;
//        }
//
//        if (strcmp(reinterpret_cast<const char *>(regs->regs[1]), "AudioRecord") == 0) {
//            LOGI("Found our target %");
//        }
//    }
//}

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

    ElfScanner g_libTargetELF = ElfScanner::createWithPath("libaudioclient.so");

    uintptr_t obtainBuffer_symbol = g_libTargetELF.findSymbol("_ZN7android11AudioRecord12obtainBufferEPNS0_6BufferEPK8timespecPS3_Pm");
    LOGI("AudioRecord::obtainBuffer Symbol at %p (%s)", (void*) obtainBuffer_symbol, "_ZN7android11AudioRecord12obtainBufferEPNS0_6BufferEPK8timespecPS3_Pm");

    hook_func((void*) obtainBuffer_symbol, (void*) obtainBuffer_hook, (void**) &obtainBuffer_backup);
    hook_func((void*) __android_log_print, (void*) log_hook, (void**) &log_backup);
}
