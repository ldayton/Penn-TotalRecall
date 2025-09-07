package audio;

import lombok.NonNull;

/**
 * Immutable audio data returned from sample reading operations.
 *
 * <p>This record encapsulates raw audio samples along with their metadata. It is designed to be a
 * simple, library-agnostic data carrier.
 *
 * @param samples Audio samples as doubles normalized to [-1.0, 1.0]. For multi-channel audio,
 *     samples are interleaved (e.g., [L0, R0, L1, R1, ...] for stereo)
 * @param sampleRate Sample rate in Hz (e.g., 44100)
 * @param channelCount Number of channels (1 for mono, 2 for stereo, etc.)
 * @param startFrame Starting frame position in the original file
 * @param frameCount Number of frames actually read (may be less than requested if EOF reached)
 */
public record AudioData(
        @NonNull double[] samples,
        int sampleRate,
        int channelCount,
        long startFrame,
        long frameCount) {

    public AudioData {
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("Sample rate must be positive: " + sampleRate);
        }
        if (channelCount <= 0) {
            throw new IllegalArgumentException("Channel count must be positive: " + channelCount);
        }
        if (startFrame < 0) {
            throw new IllegalArgumentException("Start frame cannot be negative: " + startFrame);
        }
        if (frameCount < 0) {
            throw new IllegalArgumentException("Frame count cannot be negative: " + frameCount);
        }
        if (samples.length != channelCount * frameCount && frameCount > 0) {
            throw new IllegalArgumentException(
                    "Sample array length ("
                            + samples.length
                            + ") doesn't match channelCount * frameCount ("
                            + (channelCount * frameCount)
                            + ")");
        }
    }

    /**
     * Gets the duration of this audio data in seconds.
     *
     * @return Duration in seconds
     */
    public double getDurationSeconds() {
        return (double) frameCount / sampleRate;
    }

    /**
     * Gets the starting time in seconds relative to the beginning of the file.
     *
     * @return Start time in seconds
     */
    public double getStartTimeSeconds() {
        return (double) startFrame / sampleRate;
    }

    /**
     * Gets the ending time in seconds relative to the beginning of the file.
     *
     * @return End time in seconds
     */
    public double getEndTimeSeconds() {
        return getStartTimeSeconds() + getDurationSeconds();
    }

    /**
     * Creates an empty AudioData with no samples.
     *
     * @param sampleRate Sample rate for the empty data
     * @param channelCount Channel count for the empty data
     * @param startFrame Start frame position
     * @return Empty AudioData instance
     */
    public static AudioData empty(int sampleRate, int channelCount, long startFrame) {
        return new AudioData(new double[0], sampleRate, channelCount, startFrame, 0);
    }
}
