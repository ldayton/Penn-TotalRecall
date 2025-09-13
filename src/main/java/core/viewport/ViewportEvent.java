package core.viewport;

public sealed interface ViewportEvent
        permits ViewportEvent.PlaybackUpdateEvent,
                ViewportEvent.UserZoomEvent,
                ViewportEvent.UserSeekEvent,
                ViewportEvent.CanvasResizeEvent {

    long timestamp();

    String source();

    record PlaybackUpdateEvent(long timestamp, String source, double playheadSeconds)
            implements ViewportEvent {}

    record UserZoomEvent(long timestamp, String source, int newPixelsPerSecond)
            implements ViewportEvent {}

    record UserSeekEvent(long timestamp, String source, double targetSeconds)
            implements ViewportEvent {}

    record CanvasResizeEvent(long timestamp, String source, int newWidth, int newHeight)
            implements ViewportEvent {}
}
