package core.waveform;

import core.audio.AudioMetadata;
import core.waveform.signal.WaveformProcessor;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

    // Common resolutions to pre-calculate
    private static final int[] COMMON_RESOLUTIONS = {100, 200, 400};

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
            log.info("Pre-calculated peak for {}px/s: {}", pixelsPerSecond, peak);
        }
    }

    /** Get the peak value for a specific resolution. If not cached, calculates it on demand. */
    public double getPeak(int pixelsPerSecond) {
        return peakCache.computeIfAbsent(
                pixelsPerSecond,
                pps -> {
                    log.info("Calculating peak on-demand for {}px/s", pps);
                    return calculateGlobalPeak(pps);
                });
    }

    /**
     * Calculate the global peak by sampling the entire audio file. This reads samples at regular
     * intervals and finds the maximum peak.
     */
    private double calculateGlobalPeak(int pixelsPerSecond) {
        try {
            double maxPeak = 0.0;
            int samplesProcessed = 0;

            // Process chunks at regular intervals throughout the file
            // We'll process 10-second chunks every SAMPLE_INTERVAL_SECONDS
            double chunkDuration = 10.0; // Process 10-second chunks
            double currentTime = 0.0;

            // Sample the entire audio file
            double audioDuration = metadata.durationSeconds();
            log.info("Calculating peak for {}s audio file", audioDuration);

            while (currentTime < audioDuration) {
                int chunkIndex = (int) (currentTime / chunkDuration);

                try {
                    // Process this chunk
                    double[] pixelValues =
                            processor.processAudioForDisplay(
                                    audioFilePath,
                                    chunkIndex,
                                    chunkDuration,
                                    0.25, // Pre-data overlap
                                    0.001, // Min frequency
                                    0.45, // Max frequency
                                    (int) (chunkDuration * pixelsPerSecond));

                    // Calculate peak for this chunk using the same algorithm as before
                    double chunkPeak =
                            getRenderingPeak(pixelValues, Math.max(1, pixelsPerSecond / 2));
                    maxPeak = Math.max(maxPeak, chunkPeak);

                    log.debug("Chunk {} at {}s: peak = {}", chunkIndex, currentTime, chunkPeak);

                } catch (Exception e) {
                    log.debug("Failed to process chunk at {}s: {}", currentTime, e.getMessage());
                }

                currentTime += SAMPLE_INTERVAL_SECONDS;
                samplesProcessed++;
            }

            // Apply minimum threshold to prevent excessive scaling
            if (maxPeak < MIN_PEAK) {
                log.warn("Peak {} is below minimum threshold, using {}", maxPeak, MIN_PEAK);
                maxPeak = MIN_PEAK;
            }

            log.info(
                    "Global peak calculated for {}px/s: {} (from {} samples covering {}s of {}s"
                            + " total)",
                    pixelsPerSecond,
                    maxPeak,
                    samplesProcessed,
                    Math.min(currentTime, audioDuration),
                    audioDuration);
            return maxPeak;

        } catch (Exception e) {
            log.error("Failed to calculate global peak, using default: {}", e.getMessage());
            return DEFAULT_PEAK;
        }
    }

    /**
     * Calculate rendering peak using consecutive pixel minimum. This matches the original algorithm
     * for consistency.
     */
    private double getRenderingPeak(@NonNull double[] pixelValues, int skipInitialPixels) {
        if (pixelValues.length < skipInitialPixels + 2) {
            return 0;
        }

        double maxConsecutive = 0;
        for (int i = skipInitialPixels; i < pixelValues.length - 1; i++) {
            double consecutiveVals = Math.min(pixelValues[i], pixelValues[i + 1]);
            maxConsecutive = Math.max(consecutiveVals, maxConsecutive);
        }

        return maxConsecutive;
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
}
