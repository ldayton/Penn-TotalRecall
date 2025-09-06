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
     * Update viewport position to follow playback. Only updates when playing to avoid stuttering.
     */
    public void followPlayback(double playbackPosition, double totalDuration, boolean isPlaying) {
        if (totalDuration <= 0 || !isPlaying) {
            return;
        }

        double widthSeconds = viewportWidthPixels / (double) pixelsPerSecond;
        double halfWidth = widthSeconds / 2.0;

        // Calculate centered position
        double newStart;
        if (playbackPosition < halfWidth) {
            // At the beginning - playhead moves from left to center
            newStart = 0;
        } else if (playbackPosition > totalDuration - halfWidth) {
            // At the end - keep the end visible, playhead moves from center to right
            newStart = Math.max(0, totalDuration - widthSeconds);
        } else {
            // In the middle - keep playhead centered
            newStart = playbackPosition - halfWidth;
        }

        // Only scroll forward during playback to prevent stuttering
        if (newStart > startSeconds) {
            startSeconds = newStart;
        }
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
