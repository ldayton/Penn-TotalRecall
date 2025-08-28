package waveform;

import audio.FmodCore;

/** Fluent builder for creating Waveform instances with configurable parameters. */
public final class WaveformBuilder {
    private String audioFilePath;
    private int timeResolution = WaveformRenderer.PIXELS_PER_SECOND;  // Default: 200 pixels per second
    private int amplitudeResolution = 600;  // Default height
    private boolean cachingEnabled = true;  // Default: enabled
    private final FmodCore fmodCore;

    WaveformBuilder(FmodCore fmodCore) {
        this.fmodCore = fmodCore;
    }

    /** Sets the audio file path to visualize. */
    public WaveformBuilder audioFile(String audioFilePath) {
        this.audioFilePath = audioFilePath;
        return this;
    }

    /** Sets the time resolution in pixels per second. Higher values show more detail. */
    public WaveformBuilder timeResolution(int pixelsPerSecond) {
        this.timeResolution = pixelsPerSecond;
        return this;
    }

    /** Sets the amplitude resolution in pixels. Higher values show more amplitude detail. */
    public WaveformBuilder amplitudeResolution(int heightPixels) {
        this.amplitudeResolution = heightPixels;
        return this;
    }

    /** Enables or disables caching for testing and debugging purposes. */
    public WaveformBuilder enableCaching(boolean enabled) {
        this.cachingEnabled = enabled;
        return this;
    }

    /** Builds the configured Waveform instance. */
    public Waveform build() {
        if (audioFilePath == null || audioFilePath.trim().isEmpty()) {
            throw new IllegalArgumentException("Audio file path is required");
        }

        return new Waveform(audioFilePath, timeResolution, amplitudeResolution, cachingEnabled, fmodCore);
    }
}