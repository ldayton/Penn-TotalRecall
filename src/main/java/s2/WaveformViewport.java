package s2;

import w2.TimeRange;

/**
 * Manages viewport position and zoom for the waveform display. Handles auto-scrolling during
 * playback and manual scrolling.
 */
public class WaveformViewport {

    private static final int DEFAULT_PIXELS_PER_SECOND = 200; // Show ~5 seconds on a 1000px canvas

    private double startSeconds = 0.0;
    private int pixelsPerSecond = DEFAULT_PIXELS_PER_SECOND;
    private int viewportWidthPixels;

    /** Set the viewport width in pixels. */
    public void setWidth(int pixels) {
        this.viewportWidthPixels = pixels;
    }

    /** Set the zoom level (pixels per second of audio). */
    public void setZoom(int pixelsPerSecond) {
        this.pixelsPerSecond = pixelsPerSecond;
    }

    /** Manually scroll to a specific time. */
    public void scrollTo(double seconds) {
        this.startSeconds = Math.max(0, seconds);
    }

    /**
     * Update viewport position to follow playback with smooth scrolling. Keeps playhead centered
     * (50%) when possible, but allows it to move from 0-50% at the start and 50-100% at the end of
     * the audio.
     */
    public void followPlayback(double playbackPosition, double totalDuration, boolean isPlaying) {
        if (totalDuration <= 0) {
            return;
        }

        double widthSeconds = viewportWidthPixels / (double) pixelsPerSecond;
        double halfWidth = widthSeconds / 2.0;

        // Calculate target viewport start position
        double targetStart;

        if (playbackPosition < halfWidth) {
            // Zone 1: Beginning - viewport locked at 0, playhead moves from left to center
            targetStart = 0;
        } else if (playbackPosition > totalDuration - halfWidth) {
            // Zone 3: End - viewport locked to show end, playhead moves from center to right
            targetStart = Math.max(0, totalDuration - widthSeconds);
        } else {
            // Zone 2: Middle - keep playhead centered at 50%
            targetStart = playbackPosition - halfWidth;
        }

        // Apply stabilization with dead zone to prevent jitter
        double deltaPixels = Math.abs(targetStart - startSeconds) * pixelsPerSecond;

        if (deltaPixels < 0.5) {
            // Less than half a pixel - don't move to prevent wobble
            return;
        } else if (deltaPixels < 2.0 && isPlaying) {
            // Small movement during playback - apply smoothing
            startSeconds += (targetStart - startSeconds) * 0.3; // 30% lerp for smoothness
        } else {
            // Large movement or not playing - jump directly
            startSeconds = targetStart;
        }

        // Round to avoid sub-pixel positioning
        double pixelGranularity = 1.0 / pixelsPerSecond;
        startSeconds = Math.round(startSeconds / pixelGranularity) * pixelGranularity;
    }

    /** Get the current visible time range. */
    public TimeRange getTimeRange() {
        double widthSeconds = viewportWidthPixels / (double) pixelsPerSecond;
        return new TimeRange(startSeconds, startSeconds + widthSeconds);
    }

    /** Get the current zoom level. */
    public int getPixelsPerSecond() {
        return pixelsPerSecond;
    }

    /** Get the viewport width in pixels. */
    public int getViewportWidthPixels() {
        return viewportWidthPixels;
    }

    /** Get the viewport width in seconds. */
    public double getViewportWidthSeconds() {
        return viewportWidthPixels / (double) pixelsPerSecond;
    }
}
