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
     * Update viewport position to follow playback with smooth scrolling. Keeps playhead at a fixed
     * position on screen (33% from left).
     */
    public void followPlayback(double playbackPosition, double totalDuration, boolean isPlaying) {
        if (totalDuration <= 0) {
            return;
        }

        double widthSeconds = viewportWidthPixels / (double) pixelsPerSecond;

        // Playhead stays at 33% from the left of the viewport
        double playheadPositionSeconds = widthSeconds * 0.33;

        // Calculate ideal viewport start to keep playhead at fixed position
        double idealStart = playbackPosition - playheadPositionSeconds;

        double oldStart = startSeconds;

        // Handle bounds at beginning and end of audio
        if (idealStart < 0) {
            // At the beginning - viewport locked at 0, playhead moves across screen
            startSeconds = 0;
        } else if (idealStart + widthSeconds > totalDuration) {
            // At the end - viewport locked to show end, playhead moves to right
            startSeconds = Math.max(0, totalDuration - widthSeconds);
        } else {
            // Normal case - smooth continuous scrolling
            startSeconds = idealStart;
        }

        // Debug logging
        System.out.printf(
                "followPlayback: pos=%.2f, oldStart=%.2f, newStart=%.2f, width=%.2f%n",
                playbackPosition, oldStart, startSeconds, widthSeconds);
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
