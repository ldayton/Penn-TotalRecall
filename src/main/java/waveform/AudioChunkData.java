package waveform;

/**
 * Pure audio domain data for a processed chunk.
 *
 * <p>Contains amplitude values and audio metadata without any display or pixel concepts.
 * Package-private - only used internally within audio.signal package.
 */
final class AudioChunkData {
    public final double[] amplitudeValues;
    public final double sampleRate;
    public final double peakAmplitude;
    public final int frameCount;
    public final int overlapFrames;

    AudioChunkData(
            double[] amplitudeValues,
            double sampleRate,
            double peakAmplitude,
            int frameCount,
            int overlapFrames) {
        this.amplitudeValues = amplitudeValues;
        this.sampleRate = sampleRate;
        this.peakAmplitude = peakAmplitude;
        this.frameCount = frameCount;
        this.overlapFrames = overlapFrames;
    }

    double getDurationSeconds() {
        return frameCount / sampleRate;
    }
}
