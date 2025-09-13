package core.viewport;

import core.waveform.TimeRange;

public record ViewportContext(
        double playheadSeconds, // What time (in seconds) is at the center/playhead
        int pixelsPerSecond, // Zoom level
        int canvasWidth, // Width of drawing area in pixels
        int canvasHeight // Height of drawing area in pixels
        ) {
    public ViewportContext {
        // Validation
        if (playheadSeconds < 0) {
            throw new IllegalArgumentException("Playhead position cannot be negative");
        }
        if (pixelsPerSecond <= 0) {
            throw new IllegalArgumentException("Pixels per second must be positive");
        }
        if (canvasWidth < 0) {
            throw new IllegalArgumentException("Canvas width cannot be negative");
        }
        if (canvasHeight < 0) {
            throw new IllegalArgumentException("Canvas height cannot be negative");
        }
    }

    public double getViewportStartTime() {
        if (canvasWidth == 0 || pixelsPerSecond == 0) {
            return playheadSeconds;
        }
        double widthInSeconds = canvasWidth / (double) pixelsPerSecond;
        return playheadSeconds - (widthInSeconds / 2.0);
    }

    public double getViewportEndTime() {
        if (canvasWidth == 0 || pixelsPerSecond == 0) {
            return playheadSeconds;
        }
        double widthInSeconds = canvasWidth / (double) pixelsPerSecond;
        return playheadSeconds + (widthInSeconds / 2.0);
    }

    public double getViewportWidthInSeconds() {
        if (pixelsPerSecond == 0) {
            return 0;
        }
        return canvasWidth / (double) pixelsPerSecond;
    }

    public TimeRange getTimeRange() {
        double start = Math.max(0, getViewportStartTime());
        double end = Math.max(0.001, getViewportEndTime());
        return new TimeRange(start, end);
    }

    public static ViewportContext createDefault() {
        return new ViewportContext(0, 200, 1000, 200);
    }
}
