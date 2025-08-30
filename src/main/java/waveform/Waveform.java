package waveform;

import audio.FmodCore;
import java.awt.Image;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Complete waveform representation of an audio file with chunked rendering. */
public final class Waveform {
    private static final Logger logger = LoggerFactory.getLogger(Waveform.class);

    private final String audioFilePath;
    private volatile int timeResolution;
    private volatile int amplitudeResolution;
    private final WaveformProcessor processor;
    private final WaveformRenderer renderer;
    private final PixelScaler pixelScaler;
    private volatile WaveformChunkCache cache;

    // Rendering peak may depend on time resolution; store per-resolution values
    private final java.util.concurrent.ConcurrentHashMap<Integer, Double> resolutionPeaks =
            new java.util.concurrent.ConcurrentHashMap<>();

    // Package-private constructor - only called by builder
    Waveform(String audioFilePath, int timeResolution, int amplitudeResolution, FmodCore fmodCore) {
        this.audioFilePath = audioFilePath;
        this.timeResolution = timeResolution;
        this.amplitudeResolution = amplitudeResolution;

        // Create dependencies with new - no DI complexity needed
        this.pixelScaler = new PixelScaler();
        WaveformScaler waveformScaler = new WaveformScaler();
        this.renderer = new WaveformRenderer(waveformScaler);
        this.processor = new WaveformProcessor(fmodCore, pixelScaler);

        // Cache will be set later via initializeCache() when AudioState is available
        this.cache = null;
    }

    /** Creates a new WaveformBuilder for fluent configuration. */
    public static WaveformBuilder builder(FmodCore fmodCore) {
        return new WaveformBuilder(fmodCore);
    }

    /** Renders a chunk of waveform as an image with consistent scaling across chunks. */
    public RenderedChunk renderChunk(int chunkNumber) {
        if (cache != null) {
            // Try cache first for best performance
            RenderedChunk cached =
                    cache.getChunkIfPresent(chunkNumber, timeResolution, amplitudeResolution);
            if (cached != null) {
                return cached;
            }
            // Cache miss: trigger async load for next time, but render sync now for immediate
            // display
            cache.ensureChunkAsync(chunkNumber, timeResolution, amplitudeResolution);
        }
        // Render synchronously (either no cache or cache miss)
        return new RenderedChunk(
                chunkNumber,
                renderChunkDirectConfigured(chunkNumber, timeResolution, amplitudeResolution));
    }

    /** Direct rendering with explicit configuration used by cache loader and no-cache path. */
    Image renderChunkDirectConfigured(int chunkNumber, int timeRes, int ampRes) {
        final int chunkDurationSeconds = 10;
        final double preDataSeconds = 0.25;
        final int widthPixels = timeRes * chunkDurationSeconds;
        final double chunkStartTimeSeconds = chunkNumber * chunkDurationSeconds;

        double[] audioData =
                processor.processAudioForDisplay(
                        audioFilePath,
                        chunkNumber,
                        chunkDurationSeconds,
                        preDataSeconds,
                        0.001, // Minimum valid frequency for BandPassFilter
                        0.45, // Maximum frequency matching original fullrange test
                        widthPixels);

        double peak = ensureGlobalScalingForResolution(audioData, timeRes);

        double yScale = renderer.calculateYScale(audioData, ampRes, peak);

        return renderer.renderWaveformChunk(
                audioData, widthPixels, ampRes, yScale, chunkStartTimeSeconds, peak, timeRes);
    }

    /** Thread-safe lazy initialization of global rendering peak for consistent scaling. */
    private double ensureGlobalScalingForResolution(double[] audioData, int timeRes) {
        return resolutionPeaks.computeIfAbsent(
                timeRes,
                _ -> {
                    double peak = pixelScaler.getRenderingPeak(audioData, Math.max(1, timeRes / 2));
                    logger.debug(
                            "Initialized rendering peak: {} for file: {} at {} px/s",
                            peak,
                            audioFilePath,
                            timeRes);
                    return peak;
                });
    }

    /** Returns the audio file path this waveform represents. */
    public String getAudioFilePath() {
        return audioFilePath;
    }

    /** Resets global scaling to force recalculation on next render. */
    public void resetScaling() {
        resolutionPeaks.clear();
        logger.debug("Reset global scaling cache for file: {}", audioFilePath);
    }

    /** Initialize cache with AudioState dependency - called by AudioState. */
    public void initializeCache(state.AudioState audioState) {
        if (cache == null) {
            synchronized (this) {
                if (cache == null) {
                    cache = new WaveformChunkCache(this, audioState);
                }
            }
        }
    }

    /** Clears the cache if present. */
    public void clearCache() {
        if (cache != null) {
            cache.clear();
        }
    }

    /**
     * Updates the waveform display height (amplitude resolution).
     *
     * <p>Keyed caching guarantees freshness: subsequent renders use a different cache key
     * (chunkNumber, timeResolution, newHeight), so images are recomputed without explicit
     * invalidation or cache swapping.
     */
    public void setAmplitudeResolution(int newHeightPixels) {
        if (newHeightPixels <= 0) {
            throw new IllegalArgumentException(
                    "Amplitude resolution must be > 0: " + newHeightPixels);
        }

        synchronized (this) {
            if (this.amplitudeResolution == newHeightPixels) {
                return; // no-op
            }
            this.amplitudeResolution = newHeightPixels;
        }

        logger.debug(
                "Amplitude resolution updated to {} px for file: {}",
                newHeightPixels,
                audioFilePath);
    }

    /**
     * Updates the waveform time resolution (pixels per second).
     *
     * <p>Keyed caching guarantees freshness: subsequent renders use a different cache key
     * (chunkNumber, newTimeResolution, amplitudeResolution), so images are recomputed without
     * explicit invalidation or cache swapping.
     */
    public void setTimeResolution(int newPixelsPerSecond) {
        if (newPixelsPerSecond <= 0) {
            throw new IllegalArgumentException(
                    "Time resolution must be > 0: " + newPixelsPerSecond);
        }

        synchronized (this) {
            if (this.timeResolution == newPixelsPerSecond) {
                return; // no-op
            }
            this.timeResolution = newPixelsPerSecond;
        }

        logger.debug(
                "Time resolution updated to {} px/sec for file: {}",
                newPixelsPerSecond,
                audioFilePath);
    }

    /** Current time resolution in pixels per second. */
    public int getTimeResolution() {
        return timeResolution;
    }

    /** Current amplitude resolution (image height in pixels). */
    public int getAmplitudeResolution() {
        return amplitudeResolution;
    }
}
