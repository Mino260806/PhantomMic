package tn.amin.phantom_mic.audio;

import java.io.ByteArrayOutputStream;

public class ExposedByteArrayOutputStream extends ByteArrayOutputStream {
    public byte[] getBuffer() {
        return buf;
    }
}
