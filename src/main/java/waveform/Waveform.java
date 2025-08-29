package waveform;

import audio.FmodCore;
import java.awt.Image;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Complete waveform representation of an audio file with chunked rendering. */
public final class Waveform {
    private static final Logger logger = LoggerFactory.getLogger(Waveform.class);

    private final String audioFilePath;
    private final int timeResolution;
    private volatile int amplitudeResolution;
    private final boolean cachingEnabled;
    private final WaveformProcessor processor;
    private final WaveformRenderer renderer;
    private final PixelScaler pixelScaler;
    private volatile WaveformChunkCache cache;
    private volatile state.AudioState cachedAudioState;

    private volatile double globalRenderingPeak = -1;

    // Package-private constructor - only called by builder
    Waveform(
            String audioFilePath,
            int timeResolution,
            int amplitudeResolution,
            boolean cachingEnabled,
            FmodCore fmodCore) {
        this.audioFilePath = audioFilePath;
        this.timeResolution = timeResolution;
        this.amplitudeResolution = amplitudeResolution;
        this.cachingEnabled = cachingEnabled;

        // Create dependencies with new - no DI complexity needed
        this.pixelScaler = new PixelScaler();
        WaveformScaler waveformScaler = new WaveformScaler();
        this.renderer = new WaveformRenderer(waveformScaler);
        this.processor =
                new WaveformProcessor(fmodCore, pixelScaler, waveformScaler, cachingEnabled);

        // Create cache if caching is enabled - needs AudioState but we don't have it yet
        // For now, cache will be set later via a setter
        this.cache = null;
        this.cachedAudioState = null;
    }

    /** Creates a new WaveformBuilder for fluent configuration. */
    public static WaveformBuilder builder(FmodCore fmodCore) {
        return new WaveformBuilder(fmodCore);
    }

    /** Renders a chunk of waveform as an image with consistent scaling across chunks. */
    public RenderedChunk renderChunk(int chunkNumber) {
        if (cachingEnabled && cache != null) {
            return cache.getChunk(chunkNumber);
        }
        return new RenderedChunk(chunkNumber, renderChunkDirect(chunkNumber));
    }

    /** Direct rendering without caching - used internally by cache. */
    Image renderChunkDirect(int chunkNumber) {
        final int chunkDurationSeconds = 10;
        final double preDataSeconds = 0.25;
        final int widthPixels = timeResolution * chunkDurationSeconds;
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

        ensureGlobalScaling(audioData, widthPixels);

        // Use amplitudeResolution as default height - this loses dynamic resizing but maintains API
        // simplicity
        double yScale =
                renderer.calculateYScale(audioData, amplitudeResolution, globalRenderingPeak);

        return renderer.renderWaveformChunk(
                audioData,
                widthPixels,
                amplitudeResolution,
                yScale,
                chunkStartTimeSeconds,
                globalRenderingPeak,
                timeResolution);
    }

    /** Thread-safe lazy initialization of global rendering peak for consistent scaling. */
    private void ensureGlobalScaling(double[] audioData, int widthPixels) {
        if (globalRenderingPeak < 0) {
            synchronized (this) {
                if (globalRenderingPeak < 0) {
                    globalRenderingPeak =
                            pixelScaler.getRenderingPeak(audioData, timeResolution / 2);
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

    /** Resets global scaling to force recalculation on next render. */
    public void resetScaling() {
        synchronized (this) {
            globalRenderingPeak = -1;
        }
        logger.debug("Reset global scaling for file: {}", audioFilePath);
    }

    /** Initialize caching with AudioState dependency - called by AudioState. */
    public void initializeCache(state.AudioState audioState) {
        if (cachingEnabled && cache == null) {
            synchronized (this) {
                if (cache == null) {
                    this.cachedAudioState = audioState;
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
     * Updates the waveform display height (amplitude resolution) and resets the chunk cache.
     *
     * <p>Guarantee: any call to renderChunk that starts after this method returns will render with
     * the new height. This is achieved by atomically swapping the cache instance so any in-flight
     * loads complete into the old cache, which is no longer referenced.
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

            if (cachingEnabled) {
                // Swap to a fresh cache instance to avoid any stale-height images
                if (cachedAudioState != null) {
                    this.cache = new WaveformChunkCache(this, cachedAudioState);
                } else {
                    // No audio state yet; just drop the cache reference
                    this.cache = null;
                }
            }
        }

        logger.debug(
                "Amplitude resolution updated to {} px for file: {}",
                newHeightPixels,
                audioFilePath);
    }
}
