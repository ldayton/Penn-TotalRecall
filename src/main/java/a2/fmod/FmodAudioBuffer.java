package a2.fmod;

import a2.AudioBuffer;
import lombok.NonNull;

/**
 * FMOD implementation of AudioBuffer containing raw audio samples. NOT thread-safe. Manages the
 * lifecycle of the sample data.
 */
class FmodAudioBuffer implements AudioBuffer {

    private double[] samples;
    private final int sampleRate;
    private final int channelCount;
    private final long startFrame;
    private final long frameCount;
    private volatile boolean closed = false;

    FmodAudioBuffer(
            @NonNull double[] samples,
            int sampleRate,
            int channelCount,
            long startFrame,
            long frameCount) {
        this.samples = samples;
        this.sampleRate = sampleRate;
        this.channelCount = channelCount;
        this.startFrame = startFrame;
        this.frameCount = frameCount;
    }

    @Override
    public double[] getSamples() {
        return closed ? null : samples;
    }

    @Override
    public int getSampleRate() {
        return sampleRate;
    }

    @Override
    public int getChannelCount() {
        return channelCount;
    }

    @Override
    public long getStartFrame() {
        return startFrame;
    }

    @Override
    public long getFrameCount() {
        return frameCount;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            samples = null; // Allow GC to reclaim memory
        }
    }
}
