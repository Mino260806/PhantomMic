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

    void on_buffer_chunk_loaded(jbyte* buffer, jsize size);

    bool overwrite_buffer(char* buffer, int size);

    void on_load_done();

    void unload(JNIEnv *env);

private:
    jobject j_phantomManager;

    bool m_buffer_loaded = false;
    int m_buffer_size = 16384;
    int m_buffer_write_position = 0;
    int m_buffer_read_position = 0;
    jbyte* m_buffer = nullptr;
};

#endif //PHANTOMMIC_PHANTOMBRIDGE_H
