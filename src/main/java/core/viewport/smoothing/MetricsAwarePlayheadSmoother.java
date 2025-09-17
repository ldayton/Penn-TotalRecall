package core.viewport.smoothing;

import core.audio.session.AudioSessionStateMachine;
import lombok.extern.slf4j.Slf4j;

/**
 * Decorator that adds smoothness metrics collection to any PlayheadSmoother implementation. Wraps
 * an existing smoother and collects position samples for smoothness analysis. Thread-safe for
 * concurrent access from audio and UI threads.
 */
@Slf4j
public class MetricsAwarePlayheadSmoother implements PlayheadSmoother {

    private final PlayheadSmoother delegate;
    private final SmoothingMetrics metrics;
    private volatile boolean metricsEnabled = true;

    /**
     * Create a metrics-aware wrapper around an existing smoother.
     *
     * @param delegate The underlying smoother to wrap
     * @param sampleWindowSize Number of samples to keep for metrics calculation (e.g., 100)
     */
    public MetricsAwarePlayheadSmoother(PlayheadSmoother delegate, int sampleWindowSize) {
        this.delegate = delegate;
        this.metrics = new SmoothingMetrics(sampleWindowSize);
    }

    @Override
    public SmoothingResult updateAndGetSmoothedPosition(
            long targetFrame, long deltaMs, AudioSessionStateMachine.State state) {

        // Get result from underlying smoother
        SmoothingResult result = delegate.updateAndGetSmoothedPosition(targetFrame, deltaMs, state);

        // Collect metrics if enabled
        if (metricsEnabled && state == AudioSessionStateMachine.State.PLAYING) {
            long timestamp = System.currentTimeMillis();
            metrics.addSample(timestamp, result.smoothedFrame(), targetFrame);

            // Optionally log metrics periodically (every 100 samples)
            if (timestamp % 100 == 0) {
                logMetrics();
            }
        }

        return result;
    }

    @Override
    public void reset() {
        delegate.reset();
        metrics.reset();
        log.debug("Metrics-aware smoother reset");
    }

    /**
     * Get current smoothness scores.
     *
     * @return Smoothness scores or null if insufficient data
     */
    public SmoothingMetrics.SmoothnessScores getSmoothnessScores() {
        return metrics.calculateScores();
    }

    /**
     * Enable or disable metrics collection.
     *
     * @param enabled True to enable metrics, false to disable
     */
    public void setMetricsEnabled(boolean enabled) {
        this.metricsEnabled = enabled;
        if (!enabled) {
            metrics.reset();
        }
    }

    /**
     * Get the underlying smoother being wrapped.
     *
     * @return The delegate smoother
     */
    public PlayheadSmoother getDelegate() {
        return delegate;
    }

    /** Log current smoothness metrics for debugging. */
    private void logMetrics() {
        SmoothingMetrics.SmoothnessScores scores = metrics.calculateScores();
        if (scores != null) {
            log.trace(
                    "Smoothness metrics - SPARC: {}, Jerk RMS: {}, Lag: {}ms, P95 Lag: {}ms,"
                            + " Overshoot: {}%",
                    scores.sparcScore(),
                    scores.jerkRMS(),
                    scores.lagMs(),
                    scores.p95LagMs(),
                    scores.overshoot());
        }
    }

    /**
     * Create a metrics report string for display or logging.
     *
     * @return Formatted metrics report or empty string if no data
     */
    public String getMetricsReport() {
        SmoothingMetrics.SmoothnessScores scores = metrics.calculateScores();
        if (scores == null) {
            return "Insufficient data for metrics (need at least 10 samples)";
        }

        return String.format(
                "Smoothness Metrics Report:\n"
                        + "  SPARC Score: %.3f (lower is smoother)\n"
                        + "  Jerk RMS: %.3f (lower is smoother)\n"
                        + "  Average Lag: %.1f ms\n"
                        + "  P95 Lag: %.1f ms\n"
                        + "  Overshoot: %.1f%%\n"
                        + "  Sample Count: %d\n"
                        + "  Smoother Type: %s",
                scores.sparcScore(),
                scores.jerkRMS(),
                scores.lagMs(),
                scores.p95LagMs(),
                scores.overshoot(),
                scores.sampleCount(),
                delegate.getClass().getSimpleName());
    }
}
