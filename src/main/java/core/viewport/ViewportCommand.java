package core.viewport;

/**
 * Commands that mutate viewport state. Each command represents a specific state change to be
 * applied atomically. All commands are immutable value objects.
 */
public sealed interface ViewportCommand
        permits ViewportCommand.PlaybackUpdate,
                ViewportCommand.UserZoom,
                ViewportCommand.UserSeek,
                ViewportCommand.CanvasResize {

    long timestamp();

    String source();

    record PlaybackUpdate(long timestamp, String source, double playheadSeconds)
            implements ViewportCommand {}

    record UserZoom(long timestamp, String source, int newPixelsPerSecond)
            implements ViewportCommand {}

    record UserSeek(long timestamp, String source, double targetSeconds)
            implements ViewportCommand {}

    record CanvasResize(long timestamp, String source, int newWidth, int newHeight)
            implements ViewportCommand {}
}
