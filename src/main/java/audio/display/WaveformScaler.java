package audio.display;

import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scaling utilities for waveform visualization in UI components.
 *
 * <p>This class handles the mathematical operations needed to scale audio amplitude values for
 * optimal visual display, ensuring good use of available screen space.
 */
@Singleton
public class WaveformScaler {
    private static final Logger logger = LoggerFactory.getLogger(WaveformScaler.class);

    /**
     * Calculates optimal pixel scaling factor for waveform display.
     *
     * <p>This is the exact Y-scaling algorithm from WaveformBuffer lines 276-281. It determines
     * yScale by finding the largest sustained amplitude and scaling to use most of the available
     * display height.
     *
     * @param samples the sample data to analyze (pixel-resolution data)
     * @param displayHeight available height in pixels for waveform display
     * @param biggestConsecutivePixelVals pre-calculated peak sustained amplitude
     * @return optimal pixel scaling factor (pixels per unit amplitude)
     */
    public double getPixelScale(
            double[] samples, int displayHeight, double biggestConsecutivePixelVals) {
        if (displayHeight <= 0) {
            throw new IllegalArgumentException("Display height must be > 0: " + displayHeight);
        }

        if (biggestConsecutivePixelVals <= 0) {
            logger.warn("No meaningful amplitude found, using default scale");
            return 0; // WaveformBuffer uses 0, not 1.0!
        }

        double yScale = ((displayHeight / 2) - 1) / biggestConsecutivePixelVals;

        if (Double.isInfinite(yScale) || Double.isNaN(yScale)) {
            logger.warn("yScale is infinite in magnitude, or not a number, using 0 instead");
            yScale = 0;
        }

        logger.debug(
                "Calculated pixel scale: {} (peak={}, height={})",
                yScale,
                biggestConsecutivePixelVals,
                displayHeight);
        return yScale;
    }

    /**
     * Normalizes amplitude values to a target peak level.
     *
     * @param samples sample data (modified in-place)
     * @param targetPeak desired peak amplitude after normalization
     * @return the same samples array for method chaining
     */
    public double[] normalize(double[] samples, double targetPeak) {
        if (targetPeak <= 0) {
            throw new IllegalArgumentException("Target peak must be > 0: " + targetPeak);
        }
        if (samples.length == 0) {
            return samples;
        }

        double currentPeak = 0;
        for (double sample : samples) {
            currentPeak = Math.max(currentPeak, Math.abs(sample));
        }

        if (currentPeak == 0) {
            logger.debug("All samples are zero, no normalization needed");
            return samples;
        }

        double scaleFactor = targetPeak / currentPeak;
        for (int i = 0; i < samples.length; i++) {
            samples[i] *= scaleFactor;
        }

        logger.debug(
                "Normalized {} samples: peak {} -> {} (scale={})",
                samples.length,
                currentPeak,
                targetPeak,
                scaleFactor);
        return samples;
    }

    /** Calculates amplitude statistics for analysis. */
    public AmplitudeStats getAmplitudeStats(double[] samples) {
        if (samples.length == 0) {
            return new AmplitudeStats(0, 0, 0, 0, 0, 0);
        }

        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        double sum = 0;
        double sumSquares = 0;

        for (double sample : samples) {
            min = Math.min(min, sample);
            max = Math.max(max, sample);
            sum += sample;
            sumSquares += sample * sample;
        }

        double mean = sum / samples.length;
        double rms = Math.sqrt(sumSquares / samples.length);
        double peak = Math.max(Math.abs(min), Math.abs(max));

        return new AmplitudeStats(min, max, mean, rms, peak, samples.length);
    }

    private double getRenderingPeak(double[] samples, int skipInitialSamples) {
        if (samples.length < skipInitialSamples + 2) {
            return 0;
        }

        double maxSustained = 0;
        for (int i = skipInitialSamples; i < samples.length - 1; i++) {
            double sustainedAmplitude = Math.min(Math.abs(samples[i]), Math.abs(samples[i + 1]));
            maxSustained = Math.max(maxSustained, sustainedAmplitude);
        }

        return maxSustained;
    }

    /** Container for amplitude analysis results. */
    public static class AmplitudeStats {
        public final double min;
        public final double max;
        public final double mean;
        public final double rms;
        public final double peak;
        public final int sampleCount;

        public AmplitudeStats(
                double min, double max, double mean, double rms, double peak, int sampleCount) {
            this.min = min;
            this.max = max;
            this.mean = mean;
            this.rms = rms;
            this.peak = peak;
            this.sampleCount = sampleCount;
        }

        @Override
        public String toString() {
            return String.format(
                    "AmplitudeStats{min=%.3f, max=%.3f, mean=%.3f, rms=%.3f, peak=%.3f, n=%d}",
                    min, max, mean, rms, peak, sampleCount);
        }
    }
}
