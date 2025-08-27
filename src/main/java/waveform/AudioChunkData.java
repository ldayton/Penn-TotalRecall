package waveform;

/**
 * Audio chunk with amplitude data and metadata.
 */
record AudioChunkData(
        double[] amplitudeValues,
        double sampleRate,
        double peakAmplitude,
        int frameCount,
        int overlapFrames) {

    /**
     * Chunk duration in seconds.
     */
    double getDurationSeconds() {
        return frameCount / sampleRate;
    }

    /**
     * Defensive copy of amplitude values.
     */
    double[] amplitudeValuesCopy() {
        return amplitudeValues.clone();
    }
}