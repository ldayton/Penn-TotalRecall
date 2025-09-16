package core.waveform;

/** Viewport spec for waveform rendering and caching decisions. */
public record WaveformViewportSpec(
        double startTimeSeconds,
        double endTimeSeconds,
        int viewportWidthPx,
        int viewportHeightPx,
        int pixelsPerSecond) {

    /**
     * Generate a unique ID that changes when any rendering parameter changes. This ensures we can
     * detect when a repaint is actually needed.
     */
    public String specId() {
        return String.format(
                "%.3f-%.3f-%dx%d-%d",
                startTimeSeconds,
                endTimeSeconds,
                viewportWidthPx,
                viewportHeightPx,
                pixelsPerSecond);
    }
}
