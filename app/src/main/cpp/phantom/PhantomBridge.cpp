#include <cstdint>
#include "PhantomBridge.h"

#define ENCODING_PCM_16BIT          2
#define ENCODING_PCM_24BIT_PACKED   21
#define ENCODING_PCM_32BIT          22
#define ENCODING_PCM_8BIT           3
#define ENCODING_PCM_FLOAT          4

int audioFormatToJava(int audioFormat) {
    switch (audioFormat) {
        case 0x1:
            return ENCODING_PCM_16BIT;
        case 0x2:
            return ENCODING_PCM_8BIT;
        case 0x3u:
            return ENCODING_PCM_32BIT;
        case 0x5u:
            return ENCODING_PCM_FLOAT;
        case 0x6u:
            return ENCODING_PCM_24BIT_PACKED;
        default:
            return ENCODING_PCM_8BIT;
    }
}


void PhantomBridge::update_audio_format(JNIEnv* env, int sampleRate, int audioFormat, int channelMask) {
    jclass j_phantomManagerClass = env->GetObjectClass(j_phantomManager);
    jmethodID method = env->GetMethodID(j_phantomManagerClass, "updateAudioFormat", "(III)V");
    env->CallVoidMethod(j_phantomManager, method, sampleRate, channelMask, audioFormatToJava(audioFormat));
}

void PhantomBridge::load(JNIEnv *env) {
    jclass j_phantomManagerClass = env->GetObjectClass(j_phantomManager);
    jmethodID method = env->GetMethodID(j_phantomManagerClass, "load", "()V");
    env->CallVoidMethod(j_phantomManager, method);
}

jbyte *PhantomBridge::get_buffer(JNIEnv *env, int offset, int size) {
    jclass j_phantomManagerClass = env->GetObjectClass(j_phantomManager);
    jmethodID method = env->GetMethodID(j_phantomManagerClass, "getBuffer", "(II)[B");
    jbyteArray byteArray = (jbyteArray) env->CallObjectMethod(j_phantomManager, method);

    jbyte *buffer = nullptr;
    if (byteArray != nullptr) {
        j_toBeReleasedBuffer = byteArray;
        buffer = env->GetByteArrayElements(byteArray, nullptr);
    }
    return buffer;
}

void PhantomBridge::release_buffer(JNIEnv *env, jbyte *buffer) {
    env->ReleaseByteArrayElements(j_toBeReleasedBuffer, buffer, 0);
    j_toBeReleasedBuffer = nullptr;
}

PhantomBridge::PhantomBridge(jobject j_phantomManager) : j_phantomManager(j_phantomManager) {}
