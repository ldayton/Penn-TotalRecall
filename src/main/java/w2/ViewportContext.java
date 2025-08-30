package w2;

/**
 * Viewport context for intelligent caching decisions.
 *
 * <p>Contains the essential information needed to determine optimal cache size and prefetching
 * strategy.
 */
public interface ViewportContext {

    // Time range being displayed
    double getStartTimeSeconds();

    double getEndTimeSeconds();

    // Viewport dimensions
    int getViewportWidthPx();

    int getViewportHeightPx();

    // Zoom level
    double getPixelsPerSecond();

    // Scroll behavior for predictive caching
    ScrollDirection getScrollDirection();

    enum ScrollDirection {
        BACKWARD,
        STATIONARY,
        FORWARD
    }
}
