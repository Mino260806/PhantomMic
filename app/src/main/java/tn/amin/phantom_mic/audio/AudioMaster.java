package tn.amin.phantom_mic.audio;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.github.nailik.androidresampler.Resampler;
import io.github.nailik.androidresampler.ResamplerConfiguration;
import io.github.nailik.androidresampler.data.ResamplerChannel;
import io.github.nailik.androidresampler.data.ResamplerQuality;
import tn.amin.phantom_mic.log.Logger;

public class AudioMaster {
    private static final int TIMEOUT_MS = 1000;

    private AudioFormat mOutFormat;
    private ExposedByteArrayOutputStream mBuffer;
    private int mBufferReadOffset = 0;
    private boolean mIsLoading = false;

    private final ExecutorService audioLoadExecutor = Executors.newSingleThreadExecutor();

    public void load(FileDescriptor fd) {
        if (mIsLoading) {
            Logger.d("Still loading another audio, aborting");
            return;
        }

        mIsLoading = true;

        MediaExtractor extractor = new MediaExtractor();

        try {
            extractor.setDataSource(fd);
            extractor.selectTrack(0);
            MediaFormat format = extractor.getTrackFormat(0);

            String mimeType = format.getString(MediaFormat.KEY_MIME);
            if (mimeType == null) {
                Logger.d("mimeType cannot be null");
                mBuffer = null;
                return;
            }

            MediaCodec codec = MediaCodec.createDecoderByType(mimeType);
            codec.configure(format, null, null, 0);
            codec.start();

            mBuffer = new ExposedByteArrayOutputStream();

            audioLoadExecutor.execute(() -> loadData(codec, format, extractor));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void unload() {
        mIsLoading = false;
        try {
            //noinspection ResultOfMethodCallIgnored
            audioLoadExecutor.awaitTermination(500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {
        }
        mBufferReadOffset = 0;
        if (mBuffer != null) {
            try {
                mBuffer.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void loadData(MediaCodec codec, MediaFormat format, MediaExtractor extractor) {
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

        boolean isEOS = false;
        do {
            if (!isEOS) {
                int inputBufferIndex = codec.dequeueInputBuffer(TIMEOUT_MS);
                if (inputBufferIndex >= 0) {
                    ByteBuffer inputBuffer = codec.getInputBuffer(inputBufferIndex);
                    if (inputBuffer != null) {
                        int sampleSize = extractor.readSampleData(inputBuffer, 0);
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            isEOS = true;
                        } else {
                            long presentationTimeUs = extractor.getSampleTime();
                            codec.queueInputBuffer(inputBufferIndex, 0, sampleSize, presentationTimeUs, 0);
                            extractor.advance();
                        }
                    }
                }
            }

            int outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_MS);
            if (outputBufferIndex >= 0) {
                ByteBuffer outputBuffer = codec.getOutputBuffer(outputBufferIndex);
                if (outputBuffer != null) {
                    byte[] pcmData = new byte[bufferInfo.size];
                    outputBuffer.get(pcmData);
                    outputBuffer.clear();

                    // Resample and store PCM data
                    processInBuffer(format, pcmData);
                }
                codec.releaseOutputBuffer(outputBufferIndex, false);
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                format = codec.getOutputFormat();
                Logger.d("Format changed to " + format);
                // Handle format change if necessary
            }

            if (!mIsLoading) {
                Logger.d("Loading aborted");
                break;
            }
        } while ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) == 0);

        codec.stop();
        codec.release();
        extractor.release();
        mOutFormat = null;

        Logger.d("Loading done");
        mIsLoading = false;
    }

    private void processInBuffer(MediaFormat source, byte[] bufferChunk) {
        if (bufferChunk.length == 0) {
            return;
        }

        ResamplerChannel inChannel;
        ResamplerChannel outChannel;

        if (source.getInteger(MediaFormat.KEY_CHANNEL_COUNT) == 1)
            inChannel = ResamplerChannel.MONO;
        else
            inChannel = ResamplerChannel.STEREO;

        if (mOutFormat.getChannelCount() == 1)
            outChannel = ResamplerChannel.MONO;
        else
            outChannel = ResamplerChannel.STEREO;

        ResamplerConfiguration configuration = new ResamplerConfiguration(ResamplerQuality.BEST, inChannel,
                source.getInteger(MediaFormat.KEY_SAMPLE_RATE), outChannel, mOutFormat.getSampleRate());
        if (mBuffer.size() == 0) {
            Logger.d(configuration.toString());
        }

        Resampler resampler = new Resampler(configuration);
        byte[] resampledChunk = resampler.resample(bufferChunk);
        mBuffer.write(resampledChunk, 0, resampledChunk.length);

        if (mBuffer.size() == 0) {
            Logger.d("Resampling done for first chunk (" + resampledChunk.length + ")");
        }
    }

    boolean isLoaded() {
        return mBuffer != null;
    }

    public boolean getData(float[] output, int offsetInFloats, int sizeInFloats) {
        int sizeInBytes = sizeInFloats * 4;
        int offsetInBytes = offsetInFloats * 4;

        byte[] outputBytes = new byte[sizeInBytes];
        if (getData(outputBytes, offsetInBytes, sizeInBytes)) {
            copyPCM16ToFloat(outputBytes, output);
        }
        return false;
    }

    public boolean getData(short[] output, int offsetInShorts, int sizeInShorts) {
        int sizeInBytes = sizeInShorts * 2;
        int offsetInBytes = offsetInShorts * 2;

        byte[] outputBytes = new byte[sizeInBytes];
        if (getData(outputBytes, offsetInBytes, sizeInBytes)) {
            for (int i=0; i<sizeInShorts; i++) {
                // Little endian
                output[i] = (short) ((outputBytes[i * 2] & 0xFF) |
                        ((outputBytes[i * 2 + 1] & 0xFF) << 8));
            }
            return true;
        }
        return false;
    }

    public boolean getData(byte[] output, int offsetInBytes, int sizeInBytes) {
        if (!isLoaded()) {
            Logger.d("Skipping because not loaded");
            return false;
        }

        if (mBufferReadOffset + sizeInBytes > mBuffer.size()) {
            if (mIsLoading) {
                do {
                    Logger.d("Waiting for audio decode (loaded size: " + mBuffer.size() + ", expected: " + mBufferReadOffset + sizeInBytes + ")");
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                } while (mIsLoading);
                return getData(output, offsetInBytes, sizeInBytes);
            }
            else {
                int untilBounds = mBuffer.size() - mBufferReadOffset;
                getData(output, offsetInBytes, untilBounds);
                mBufferReadOffset = 0;
                return getData(output, offsetInBytes + untilBounds, sizeInBytes-untilBounds);
            }
        }

        System.arraycopy(mBuffer.getBuffer(), mBufferReadOffset, output, offsetInBytes, sizeInBytes);
        mBufferReadOffset += sizeInBytes;
        return true;
    }

    public void setFormat(AudioFormat format) {
        mOutFormat = format;
    }

    public void setFormat(int sampleRate, int channelConfig, int encoding) {
        mOutFormat = new AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setChannelMask(getChannelMaskFromLegacyConfig(channelConfig,
                        true/*allow legacy configurations*/))
                .setEncoding(encoding)
                .setSampleRate(sampleRate)
                .build();
    }

    public AudioFormat getFormat() {
        return mOutFormat;
    }

    private static int getChannelMaskFromLegacyConfig(int inChannelConfig,
                                                      boolean allowLegacyConfig) {
        int mask;
        switch (inChannelConfig) {
            case AudioFormat.CHANNEL_IN_DEFAULT: // AudioFormat.CHANNEL_CONFIGURATION_DEFAULT
            case AudioFormat.CHANNEL_IN_MONO:
            case AudioFormat.CHANNEL_CONFIGURATION_MONO:
                mask = AudioFormat.CHANNEL_IN_MONO;
                break;
            case AudioFormat.CHANNEL_IN_STEREO:
            case AudioFormat.CHANNEL_CONFIGURATION_STEREO:
                mask = AudioFormat.CHANNEL_IN_STEREO;
                break;
            case (AudioFormat.CHANNEL_IN_FRONT | AudioFormat.CHANNEL_IN_BACK):
                mask = inChannelConfig;
                break;
            default:
                throw new IllegalArgumentException("Unsupported channel configuration.");
        }

        if (!allowLegacyConfig && ((inChannelConfig == AudioFormat.CHANNEL_CONFIGURATION_MONO)
                || (inChannelConfig == AudioFormat.CHANNEL_CONFIGURATION_STEREO))) {
            // only happens with the constructor that uses AudioAttributes and AudioFormat
            throw new IllegalArgumentException("Unsupported deprecated configuration.");
        }

        return mask;
    }

    private static float[] copyPCM16ToFloat(byte[] byteBuffer, float[] floatBuffer) {
        if (byteBuffer.length % 2 != 0) {
            throw new IllegalArgumentException("The length of the byte buffer must be even.");
        }

        int sampleCount = byteBuffer.length / 2;

        for (int i = 0; i < sampleCount; i++) {
            int byteIndex = i * 2;

            // Combine the two bytes to form a 16-bit short
            int lowByte = byteBuffer[byteIndex] & 0xFF;
            int highByte = byteBuffer[byteIndex + 1] & 0xFF;

            int sample = (highByte << 8) | lowByte;
            if (sample > 32767) sample -= 65536; // Handle signed 16-bit

            // Normalize the sample to the range of -1.0 to 1.0
            floatBuffer[i] = sample / 32768.0f;
        }

        return floatBuffer;
    }
}
