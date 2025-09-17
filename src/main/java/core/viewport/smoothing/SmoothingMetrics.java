package core.viewport.smoothing;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

/**
 * Collects and analyzes smoothness metrics for playhead movement. Stores a sliding window of
 * position samples for calculating various smoothness scores. Thread-safe for concurrent updates
 * from audio thread and reads from UI thread.
 */
public class SmoothingMetrics {

    /** Sample of position at a point in time */
    public record PositionSample(long timestamp, long position, long targetPosition) {}

    /** Smoothness scores calculated from position samples */
    public record SmoothnessScores(
            double sparcScore, // Spectral Arc Length - lower is smoother
            double jerkRMS, // RMS of jerk (3rd derivative) - lower is smoother
            double lagMs, // Average lag behind target in milliseconds
            double p95LagMs, // 95th percentile lag in milliseconds
            double p99LagMs, // 99th percentile lag in milliseconds
            double maxLagMs, // Maximum lag observed in milliseconds
            double overshoot, // Percentage of samples that overshot target
            int sampleCount // Number of samples in calculation
            ) {}

    private final int maxSamples;
    private final Deque<PositionSample> samples;
    private final Object lock = new Object();

    public SmoothingMetrics(int maxSamples) {
        this.maxSamples = maxSamples;
        this.samples = new ArrayDeque<>(maxSamples);
    }

    /**
     * Add a position sample for metrics calculation.
     *
     * @param timestamp Time in milliseconds
     * @param smoothedPosition The smoothed position from the smoother
     * @param targetPosition The actual target position
     */
    public void addSample(long timestamp, long smoothedPosition, long targetPosition) {
        synchronized (lock) {
            samples.addLast(new PositionSample(timestamp, smoothedPosition, targetPosition));
            while (samples.size() > maxSamples) {
                samples.removeFirst();
            }
        }
    }

    /**
     * Calculate smoothness scores from recent samples. Requires at least 10 samples for meaningful
     * results.
     *
     * @return Smoothness scores or null if insufficient data
     */
    public SmoothnessScores calculateScores() {
        synchronized (lock) {
            if (samples.size() < 10) {
                return null; // Not enough data
            }

            PositionSample[] sampleArray = samples.toArray(new PositionSample[0]);

            double sparcScore = calculateSPARC(sampleArray);
            double jerkRMS = calculateJerkRMS(sampleArray);
            double lagMs = calculateAverageLag(sampleArray);
            double p95LagMs = calculateP95Lag(sampleArray);
            double p99LagMs = calculateP99Lag(sampleArray);
            double maxLagMs = calculateMaxLag(sampleArray);
            double overshoot = calculateOvershoot(sampleArray);

            return new SmoothnessScores(
                    sparcScore,
                    jerkRMS,
                    lagMs,
                    p95LagMs,
                    p99LagMs,
                    maxLagMs,
                    overshoot,
                    sampleArray.length);
        }
    }

    /**
     * Calculate SPARC (Spectral Arc Length) - a frequency-domain smoothness metric. Lower values
     * indicate smoother movement. Based on: Balasubramanian et al. (2012) "On the analysis of
     * movement smoothness"
     */
    private double calculateSPARC(PositionSample[] samples) {
        if (samples.length < 3) return Double.NaN;

        // Calculate velocities
        double[] velocities = new double[samples.length - 1];
        for (int i = 0; i < velocities.length; i++) {
            long posDiff = samples[i + 1].position - samples[i].position;
            long timeDiff = samples[i + 1].timestamp - samples[i].timestamp;
            if (timeDiff > 0) {
                velocities[i] = (double) posDiff / timeDiff;
            }
        }

        // Simple FFT would go here - for now using a simplified version
        // that calculates spectral arc length from velocity changes
        double arcLength = 0;
        for (int i = 1; i < velocities.length; i++) {
            double diff = velocities[i] - velocities[i - 1];
            arcLength += Math.sqrt(1 + diff * diff);
        }

        // Normalize by movement duration and distance
        long duration = samples[samples.length - 1].timestamp - samples[0].timestamp;
        long distance = Math.abs(samples[samples.length - 1].position - samples[0].position);

        if (duration > 0 && distance > 0) {
            return -arcLength * duration / distance; // Negative so lower is smoother
        }
        return 0;
    }

    /**
     * Calculate RMS of jerk (third derivative of position). Lower values indicate smoother
     * movement.
     */
    private double calculateJerkRMS(PositionSample[] samples) {
        if (samples.length < 4) return Double.NaN;

        double jerkSum = 0;
        int jerkCount = 0;

        for (int i = 3; i < samples.length; i++) {
            // Calculate jerk using finite differences
            long p0 = samples[i - 3].position;
            long p1 = samples[i - 2].position;
            long p2 = samples[i - 1].position;
            long p3 = samples[i].position;

            long t0 = samples[i - 3].timestamp;
            long t1 = samples[i - 2].timestamp;
            long t2 = samples[i - 1].timestamp;
            long t3 = samples[i].timestamp;

            if (t3 - t0 > 0) {
                // Third-order finite difference approximation
                double dt = (t3 - t0) / 3.0;
                if (dt > 0) {
                    double jerk = (p3 - 3 * p2 + 3 * p1 - p0) / (dt * dt * dt);
                    jerkSum += jerk * jerk;
                    jerkCount++;
                }
            }
        }

        return jerkCount > 0 ? Math.sqrt(jerkSum / jerkCount) : 0;
    }

    /** Calculate average lag behind target position. */
    private double calculateAverageLag(PositionSample[] samples) {
        double totalLag = 0;
        int lagCount = 0;

        for (PositionSample sample : samples) {
            long lag = sample.targetPosition - sample.position;
            if (lag > 0) { // Only count when behind
                totalLag += lag;
                lagCount++;
            }
        }

        // Convert to milliseconds assuming 44.1kHz sample rate
        double avgLagFrames = lagCount > 0 ? totalLag / lagCount : 0;
        return avgLagFrames / 44.1; // frames to ms at 44.1kHz
    }

    /** Calculate 95th percentile lag behind target position. */
    private double calculateP95Lag(PositionSample[] samples) {
        // Collect only positive lag values (when behind target)
        List<Double> positiveLags = new ArrayList<>();
        for (PositionSample sample : samples) {
            long lag = sample.targetPosition - sample.position;
            if (lag > 0) {
                // Convert to milliseconds assuming 44.1kHz sample rate
                positiveLags.add(lag / 44.1);
            }
        }

        // Return 0 if no positive lags (smoother always ahead or at target)
        if (positiveLags.isEmpty()) {
            return 0;
        }

        // Convert to array and sort
        double[] lags = positiveLags.stream().mapToDouble(Double::doubleValue).toArray();
        Arrays.sort(lags);

        // Calculate 95th percentile index
        int p95Index = (int) Math.ceil(0.95 * lags.length) - 1;
        p95Index = Math.max(0, Math.min(lags.length - 1, p95Index));

        return lags[p95Index];
    }

    /** Calculate 99th percentile lag behind target position. */
    private double calculateP99Lag(PositionSample[] samples) {
        // Collect only positive lag values (when behind target)
        List<Double> positiveLags = new ArrayList<>();
        for (PositionSample sample : samples) {
            long lag = sample.targetPosition - sample.position;
            if (lag > 0) {
                // Convert to milliseconds assuming 44.1kHz sample rate
                positiveLags.add(lag / 44.1);
            }
        }

        // Return 0 if no positive lags (smoother always ahead or at target)
        if (positiveLags.isEmpty()) {
            return 0;
        }

        // Convert to array and sort
        double[] lags = positiveLags.stream().mapToDouble(Double::doubleValue).toArray();
        Arrays.sort(lags);

        // Calculate 99th percentile index
        int p99Index = (int) Math.ceil(0.99 * lags.length) - 1;
        p99Index = Math.max(0, Math.min(lags.length - 1, p99Index));

        return lags[p99Index];
    }

    /** Calculate maximum lag behind target position. */
    private double calculateMaxLag(PositionSample[] samples) {
        double maxLag = 0;

        for (PositionSample sample : samples) {
            long lag = sample.targetPosition - sample.position;
            if (lag > 0) {
                // Convert to milliseconds assuming 44.1kHz sample rate
                double lagMs = lag / 44.1;
                maxLag = Math.max(maxLag, lagMs);
            }
        }

        return maxLag;
    }

    /** Calculate percentage of samples that overshot the target. */
    private double calculateOvershoot(PositionSample[] samples) {
        int overshootCount = 0;

        for (int i = 1; i < samples.length; i++) {
            long prevDistance = samples[i - 1].targetPosition - samples[i - 1].position;
            long currDistance = samples[i].targetPosition - samples[i].position;

            // Check if we crossed over the target (sign change from positive to negative)
            if (prevDistance > 0 && currDistance < 0) {
                overshootCount++;
            }
        }

        return samples.length > 1 ? (100.0 * overshootCount) / (samples.length - 1) : 0;
    }

    /** Clear all collected samples. */
    public void reset() {
        synchronized (lock) {
            samples.clear();
        }
    }
}
