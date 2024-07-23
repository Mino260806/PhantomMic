//
// Created by amin on 7/23/24.
//

#ifndef PHANTOMMIC_PHANTOMBRIDGE_H
#define PHANTOMMIC_PHANTOMBRIDGE_H

#include <jni.h>

class PhantomBridge {
public:
    PhantomBridge(jobject j_phantomManager);

    void update_audio_format(JNIEnv* env, int sampleRate, int audioFormat, int channelMask);

    void load(JNIEnv* env);

    jbyte* get_buffer(JNIEnv* env, int offset, int size);

    void release_buffer(JNIEnv* env, jbyte* buffer);

private:
    jobject j_phantomManager;

    jbyteArray j_toBeReleasedBuffer;
};

#endif //PHANTOMMIC_PHANTOMBRIDGE_H
