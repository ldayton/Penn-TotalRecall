package waveform;

import audio.FmodCore;

/** Fluent builder for creating Waveform instances with configurable parameters. */
public final class WaveformBuilder {
    private static final int DEFAULT_TIME_RESOLUTION_PX_PER_SEC = 50;
    private static final int DEFAULT_AMPLITUDE_RESOLUTION_PX = 300;

    private String audioFilePath;
    private int timeResolution = DEFAULT_TIME_RESOLUTION_PX_PER_SEC; // Default: 50 px/sec
    private int amplitudeResolution = DEFAULT_AMPLITUDE_RESOLUTION_PX; // Default: 300 px height
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

    /** Builds the configured Waveform instance. */
    public Waveform build() {
        if (audioFilePath == null || audioFilePath.trim().isEmpty()) {
            throw new IllegalArgumentException("Audio file path is required");
        }

        return new Waveform(audioFilePath, timeResolution, amplitudeResolution, fmodCore);
    }
}
