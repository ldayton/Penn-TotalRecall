package core.waveform;

import lombok.NonNull;

/** Viewport context for intelligent caching decisions. */
public record ViewportContext(
        double startTimeSeconds,
        double endTimeSeconds,
        int viewportWidthPx,
        int viewportHeightPx,
        int pixelsPerSecond,
        @NonNull ScrollDirection scrollDirection) {

    public enum ScrollDirection {
        BACKWARD,
        FORWARD
    }
}
