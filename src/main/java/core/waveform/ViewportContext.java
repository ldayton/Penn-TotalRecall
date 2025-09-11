package core.waveform;

/** Viewport context for intelligent caching decisions. */
public record ViewportContext(
        double startTimeSeconds,
        double endTimeSeconds,
        int viewportWidthPx,
        int viewportHeightPx,
        int pixelsPerSecond) {}
