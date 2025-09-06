package s2;

import jakarta.inject.Singleton;

/**
 * Tracks and interpolates playback position for smooth visual updates.
 *
 * <p>Audio callbacks update the real position periodically (~96ms intervals). This class
 * interpolates between those updates using elapsed time to provide smooth 60 FPS position updates
 * for the UI.
 */
@Singleton
public class InterpolatedPlaybackTracker {

    private double lastRealPosition = 0.0;
    private long lastUpdateTimeNanos = System.nanoTime();
    private double interpolatedPosition = 0.0;
    private boolean isPlaying = false;
    private final double playbackRate = 1.0; // Normal speed playback

    // Maximum allowed drift before forcing sync (100ms)
    private static final double MAX_DRIFT_SECONDS = 0.1;

    /**
     * Update with the real playback position from audio callbacks. This should be called whenever
     * the audio system reports position.
     */
    public synchronized void updateRealPosition(double positionSeconds) {
        double previousInterpolated = interpolatedPosition;
        lastRealPosition = positionSeconds;

        // Check for drift and correct if needed
        double drift = Math.abs(previousInterpolated - positionSeconds);
        if (drift > MAX_DRIFT_SECONDS) {
            // Too much drift, snap to real position
            interpolatedPosition = positionSeconds;
        } else {
            // Small drift, keep interpolated position for smoothness
            // It will gradually converge through interpolation
            interpolatedPosition = positionSeconds;
        }

        lastUpdateTimeNanos = System.nanoTime();
    }

    /**
     * Get the interpolated position for smooth visual updates. Called every frame (60 FPS) by the
     * rendering system.
     */
    public synchronized double getInterpolatedPosition() {
        if (!isPlaying) {
            return lastRealPosition;
        }

        long nowNanos = System.nanoTime();
        double elapsedSeconds = (nowNanos - lastUpdateTimeNanos) / 1_000_000_000.0;

        // Interpolate forward from last known position
        // Don't update lastUpdateTimeNanos here - only update it when we get real position updates
        interpolatedPosition = lastRealPosition + (elapsedSeconds * playbackRate);

        return interpolatedPosition;
    }

    /**
     * Reset position immediately (for seek operations). This prevents interpolation artifacts when
     * jumping to a new position.
     */
    public synchronized void reset(double positionSeconds) {
        lastRealPosition = positionSeconds;
        interpolatedPosition = positionSeconds;
        lastUpdateTimeNanos = System.nanoTime();
    }

    /** Set playing state. When paused, interpolation stops. */
    public synchronized void setPlaying(boolean playing) {
        if (!this.isPlaying && playing) {
            // Starting playback, reset time reference to now
            lastUpdateTimeNanos = System.nanoTime();
            // Ensure we start from the correct position
            interpolatedPosition = lastRealPosition;
        } else if (this.isPlaying && !playing) {
            // Stopping playback, save current interpolated position
            lastRealPosition = interpolatedPosition;
        }
        this.isPlaying = playing;
    }

    /** Get current playing state. */
    public synchronized boolean isPlaying() {
        return isPlaying;
    }
}
