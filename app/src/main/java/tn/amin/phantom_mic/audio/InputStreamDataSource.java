package tn.amin.phantom_mic.audio;

import android.media.MediaDataSource;
import java.io.IOException;
import java.io.InputStream;

public class InputStreamDataSource extends MediaDataSource {
    private InputStream inputStream;
    private long fileSize;

    public InputStreamDataSource(InputStream inputStream, long fileSize) {
        this.inputStream = inputStream;
        this.fileSize = fileSize;
    }

    @Override
    public int readAt(long position, byte[] buffer, int offset, int size) throws IOException {
        if (position >= fileSize) {
            return -1; // End of file
        }
        inputStream.reset();
        inputStream.skip(position);
        return inputStream.read(buffer, offset, size);
    }

    @Override
    public long getSize() throws IOException {
        return fileSize;
    }

    @Override
    public void close() throws IOException {
        inputStream.close();
    }
}