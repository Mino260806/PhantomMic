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

HookFunType hook_func;
UnhookFunType unhook_func;

void on_library_loaded(const char *name, void *handle) {
//    LOGI("Library Loaded %s", name);
}


extern "C" [[gnu::visibility("default")]] [[gnu::used]]
jint JNI_OnLoad(JavaVM *jvm, void*) {
    JNIEnv *env = nullptr;
    jvm->GetEnv((void **)&env, JNI_VERSION_1_6);
    LOGI("JNI_OnLoad");

    return JNI_VERSION_1_6;
}

extern "C" [[gnu::visibility("default")]] [[gnu::used]]
NativeOnModuleLoaded native_init(const NativeAPIEntries *entries) {
    hook_func = entries->hook_func;
    unhook_func = entries->unhook_func;
    return on_library_loaded;
}
