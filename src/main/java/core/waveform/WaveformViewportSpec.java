package core.waveform;

/** Viewport spec for waveform rendering and caching decisions. */
public record WaveformViewportSpec(
        double startTimeSeconds,
        double endTimeSeconds,
        int viewportWidthPx,
        int viewportHeightPx,
        int pixelsPerSecond) {}
