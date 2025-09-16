package core.waveform;

import core.audio.AudioMetadata;
import core.viewport.ViewportDefaults;
import core.waveform.signal.WaveformProcessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Dedicated class for calculating and caching waveform peak values. Calculates the global peak from
 * the entire audio file to ensure consistent and deterministic scaling across all segments.
 */
@Slf4j
public class WaveformPeakDetector {

    private final WaveformProcessor processor;
    private final String audioFilePath;
    private final AudioMetadata metadata;
    private final Map<Integer, Double> peakCache = new ConcurrentHashMap<>();

    // Sample the audio every N seconds for efficiency
    private static final double SAMPLE_INTERVAL_SECONDS = 5.0;
    private static final double MIN_PEAK = 0.01; // Prevent excessive scaling
    private static final double DEFAULT_PEAK = 0.1; // Fallback if calculation fails

    // Zoom factor used by ViewportSessionManager
    private static final double ZOOM_FACTOR = 1.5;
    private static final int ZOOM_LEVELS_TO_CACHE = 2; // Cache 2 levels in each direction

    // Thread pool for parallel chunk processing
    private static final ExecutorService executor =
            Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    // Common resolutions calculated dynamically based on zoom factor
    private static final int[] COMMON_RESOLUTIONS = calculateCommonResolutions();

    public WaveformPeakDetector(
            @NonNull String audioFilePath,
            @NonNull WaveformProcessor processor,
            @NonNull AudioMetadata metadata) {
        this.audioFilePath = audioFilePath;
        this.processor = processor;
        this.metadata = metadata;
    }

    /**
     * Initialize the detector by pre-calculating peaks for common resolutions. This should be
     * called once when the waveform is created.
     */
    public void initialize() {
        log.info("Initializing peak detector for file: {}", audioFilePath);

        for (int pixelsPerSecond : COMMON_RESOLUTIONS) {
            double peak = calculateGlobalPeak(pixelsPerSecond);
            peakCache.put(pixelsPerSecond, peak);
            log.trace("Pre-calculated peak for {}px/s: {}", pixelsPerSecond, peak);
        }
    }

    /** Get the peak value for a specific resolution. If not cached, calculates it on demand. */
    public double getPeak(int pixelsPerSecond) {
        return peakCache.computeIfAbsent(
                pixelsPerSecond,
                pps -> {
                    log.trace("Calculating peak on-demand for {}px/s", pps);
                    return calculateGlobalPeak(pps);
                });
    }

    /**
     * Calculate the global peak by sampling the entire audio file. This reads samples at regular
     * intervals and finds the maximum peak.
     */
    private double calculateGlobalPeak(int pixelsPerSecond) {
        try {
            double audioDuration = metadata.durationSeconds();
            log.trace("Calculating peak for {}s audio file", audioDuration);

            // Build list of time points to sample
            List<Double> sampleTimes = new ArrayList<>();
            double currentTime = 0.0;
            while (currentTime < audioDuration) {
                sampleTimes.add(currentTime);
                currentTime += SAMPLE_INTERVAL_SECONDS;
            }

            // Process chunks in parallel
            List<CompletableFuture<Double>> futures = new ArrayList<>();
            for (double time : sampleTimes) {
                futures.add(
                        CompletableFuture.supplyAsync(
                                () -> {
                                    int chunkIndex =
                                            (int)
                                                    (time
                                                            / WaveformProcessor
                                                                    .STANDARD_CHUNK_DURATION_SECONDS);
                                    try {
                                        double[] pixelValues =
                                                processor.processAudioForDisplay(
                                                        audioFilePath,
                                                        chunkIndex,
                                                        (int)
                                                                (WaveformProcessor
                                                                                .STANDARD_CHUNK_DURATION_SECONDS
                                                                        * pixelsPerSecond));

                                        double chunkPeak =
                                                getRenderingPeak(
                                                        pixelValues,
                                                        Math.max(1, pixelsPerSecond / 2));
                                        log.trace(
                                                "Chunk {} at {}s: peak = {}",
                                                chunkIndex,
                                                time,
                                                chunkPeak);
                                        return chunkPeak;
                                    } catch (Exception e) {
                                        log.debug(
                                                "Failed to process chunk at {}s: {}",
                                                time,
                                                e.getMessage());
                                        return 0.0;
                                    }
                                },
                                executor));
            }

            // Wait for all chunks and find maximum peak
            double maxPeak = 0.0;
            for (CompletableFuture<Double> future : futures) {
                double chunkPeak = future.join();
                maxPeak = Math.max(maxPeak, chunkPeak);
            }

            // Apply minimum threshold to prevent excessive scaling
            if (maxPeak < MIN_PEAK) {
                log.warn("Peak {} is below minimum threshold, using {}", maxPeak, MIN_PEAK);
                maxPeak = MIN_PEAK;
            }

            log.trace(
                    "Global peak calculated for {}px/s: {} (from {} samples covering {}s of {}s"
                            + " total)",
                    pixelsPerSecond,
                    maxPeak,
                    sampleTimes.size(),
                    Math.min(currentTime, audioDuration),
                    audioDuration);
            return maxPeak;

        } catch (Exception e) {
            log.error("Failed to calculate global peak, using default: {}", e.getMessage());
            return DEFAULT_PEAK;
        }
    }

    /** Calculate rendering peak using maximum absolute value for accurate peak detection. */
    private double getRenderingPeak(@NonNull double[] pixelValues, int skipInitialPixels) {
        if (pixelValues.length <= skipInitialPixels) {
            return 0;
        }

        double maxPeak = 0;

        for (int i = skipInitialPixels; i < pixelValues.length; i++) {
            double value = Math.abs(pixelValues[i]);
            maxPeak = Math.max(maxPeak, value);
        }

        return maxPeak;
    }

    /** Clear the cache and recalculate peaks. Useful if the audio file changes or for testing. */
    public void reset() {
        log.info("Resetting peak cache");
        peakCache.clear();
        initialize();
    }

    /** Get the number of cached peak values. */
    public int getCacheSize() {
        return peakCache.size();
    }

    /**
     * Dynamically calculates common resolutions based on the zoom factor. Pre-calculates
     * resolutions for N zoom levels in and out from the default.
     */
    private static int[] calculateCommonResolutions() {
        int defaultRes = ViewportDefaults.DEFAULT_PIXELS_PER_SECOND;
        List<Integer> resolutions = new ArrayList<>();

        // Add zoom-out resolutions
        for (int i = ZOOM_LEVELS_TO_CACHE; i > 0; i--) {
            int zoomOutRes = (int) Math.round(defaultRes / Math.pow(ZOOM_FACTOR, i));
            resolutions.add(zoomOutRes);
        }

        // Add default resolution
        resolutions.add(defaultRes);

        // Add zoom-in resolutions
        for (int i = 1; i <= ZOOM_LEVELS_TO_CACHE; i++) {
            int zoomInRes = (int) Math.round(defaultRes * Math.pow(ZOOM_FACTOR, i));
            resolutions.add(zoomInRes);
        }

        return resolutions.stream().mapToInt(Integer::intValue).toArray();
    }
}
