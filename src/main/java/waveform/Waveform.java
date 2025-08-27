package waveform;

import audio.FmodCore;
import java.awt.Image;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ui.UiConstants;

/** Complete waveform representation of an audio file with chunked rendering. */
public final class Waveform {
    private static final Logger logger = LoggerFactory.getLogger(Waveform.class);

    private final String audioFilePath;
    private final FrequencyRange filterRange;
    private final WaveformProcessor processor;
    private final WaveformRenderer renderer;
    private final PixelScaler pixelScaler;

    private volatile double globalRenderingPeak = -1;

    public Waveform(
            String audioFilePath, double minFrequency, double maxFrequency, FmodCore fmodCore) {
        if (audioFilePath == null || audioFilePath.trim().isEmpty()) {
            throw new IllegalArgumentException("Audio file path cannot be null or empty");
        }

        this.audioFilePath = audioFilePath;
        this.filterRange = new FrequencyRange(minFrequency, maxFrequency);

        // Create dependencies with new - no DI complexity needed
        this.pixelScaler = new PixelScaler();
        WaveformScaler waveformScaler = new WaveformScaler();
        this.renderer = new WaveformRenderer(waveformScaler);
        this.processor = new WaveformProcessor(fmodCore, pixelScaler, waveformScaler);
    }

    /** Renders a chunk of waveform as an image with consistent scaling across chunks. */
    public Image renderChunk(int chunkNumber, int heightPixels) {
        final int chunkDurationSeconds = 10;
        final double preDataSeconds = 0.25;
        final int widthPixels = UiConstants.zoomlessPixelsPerSecond * chunkDurationSeconds;
        final double chunkStartTimeSeconds = chunkNumber * chunkDurationSeconds;

        double[] audioData =
                processor.processAudioForDisplay(
                        audioFilePath,
                        chunkNumber,
                        chunkDurationSeconds,
                        preDataSeconds,
                        filterRange.minFrequency(),
                        filterRange.maxFrequency(),
                        widthPixels);

        ensureGlobalScaling(audioData, widthPixels);

        double yScale = renderer.calculateYScale(audioData, heightPixels, globalRenderingPeak);

        return renderer.renderWaveformChunk(
                audioData,
                widthPixels,
                heightPixels,
                yScale,
                chunkStartTimeSeconds,
                globalRenderingPeak);
    }

    /** Thread-safe lazy initialization of global rendering peak for consistent scaling. */
    private void ensureGlobalScaling(double[] audioData, int widthPixels) {
        if (globalRenderingPeak < 0) {
            synchronized (this) {
                if (globalRenderingPeak < 0) {
                    globalRenderingPeak =
                            pixelScaler.getRenderingPeak(
                                    audioData, UiConstants.zoomlessPixelsPerSecond / 2);
                    logger.debug(
                            "Initialized global rendering peak: {} for file: {}",
                            globalRenderingPeak,
                            audioFilePath);
                }
            }
        }
    }

    /** Returns the audio file path this waveform represents. */
    public String getAudioFilePath() {
        return audioFilePath;
    }

    /** Returns the frequency filter range applied to this waveform. */
    public FrequencyRange getFilterRange() {
        return filterRange;
    }

    /** Resets global scaling to force recalculation on next render. */
    public void resetScaling() {
        synchronized (this) {
            globalRenderingPeak = -1;
        }
        logger.debug("Reset global scaling for file: {}", audioFilePath);
    }
}
