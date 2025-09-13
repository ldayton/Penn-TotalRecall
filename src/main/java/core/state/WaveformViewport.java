package core.state;

import core.dispatch.EventDispatchBus;
import core.dispatch.Subscribe;
import core.events.SeekEvent;
import core.events.SeekScreenEvent;
import core.events.ZoomEvent;
import core.waveform.TimeRange;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.NonNull;

/**
 * Manages viewport position and zoom for the waveform display. Handles auto-scrolling during
 * playback and manual scrolling.
 */
@Singleton
public class WaveformViewport {

    private static final int DEFAULT_PIXELS_PER_SECOND = 200; // Show ~5 seconds on a 1000px canvas
    private static final int MIN_PIXELS_PER_SECOND = 50;
    private static final int MAX_PIXELS_PER_SECOND = 800;
    private static final double ZOOM_FACTOR = 1.5;

    private double startSeconds = 0.0;
    private int pixelsPerSecond = DEFAULT_PIXELS_PER_SECOND;
    private int viewportWidthPixels;
    private final EventDispatchBus eventBus;
    private final WaveformSessionDataSource sessionDataSource;

    @Inject
    public WaveformViewport(
            @NonNull EventDispatchBus eventBus,
            @NonNull WaveformSessionDataSource sessionDataSource) {
        this.eventBus = eventBus;
        this.sessionDataSource = sessionDataSource;
        eventBus.subscribe(this);
    }

    @Subscribe
    public void onZoomRequested(@NonNull ZoomEvent event) {
        int newZoom =
                event.direction() == ZoomEvent.Direction.IN
                        ? (int) (pixelsPerSecond * ZOOM_FACTOR)
                        : (int) (pixelsPerSecond / ZOOM_FACTOR);

        if (event.direction() == ZoomEvent.Direction.IN && newZoom <= MAX_PIXELS_PER_SECOND) {
            setZoom(newZoom);
        } else if (event.direction() == ZoomEvent.Direction.OUT
                && newZoom >= MIN_PIXELS_PER_SECOND) {
            setZoom(newZoom);
        }
    }

    @Subscribe
    public void onScreenSeekRequested(@NonNull SeekScreenEvent event) {
        // Get current position from session data source
        sessionDataSource
                .getPlaybackPosition()
                .ifPresent(
                        currentPositionSeconds -> {
                            // Calculate viewport width in seconds
                            double viewportSeconds = getViewportWidthSeconds();

                            // Calculate new position based on direction
                            double targetPositionSeconds =
                                    event.direction() == SeekScreenEvent.Direction.FORWARD
                                            ? currentPositionSeconds + viewportSeconds
                                            : currentPositionSeconds - viewportSeconds;

                            // Ensure within bounds and convert to frames
                            sessionDataSource
                                    .getTotalDuration()
                                    .ifPresent(
                                            totalDuration -> {
                                                sessionDataSource
                                                        .getSampleRate()
                                                        .ifPresent(
                                                                sampleRate -> {
                                                                    double boundedPosition =
                                                                            Math.max(
                                                                                    0,
                                                                                    Math.min(
                                                                                            targetPositionSeconds,
                                                                                            totalDuration));

                                                                    // Convert to frames using
                                                                    // actual sample rate
                                                                    long targetFrame =
                                                                            (long)
                                                                                    (boundedPosition
                                                                                            * sampleRate);

                                                                    // Publish seek event
                                                                    eventBus.publish(
                                                                            new SeekEvent(
                                                                                    targetFrame));
                                                                });
                                            });
                        });
    }

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
     * Update viewport position to follow playback. Keeps playhead always centered at 50% of the
     * viewport. The waveform scrolls underneath the fixed center playhead.
     */
    public void followPlayback(double playbackPosition, double totalDuration, boolean isPlaying) {
        if (totalDuration <= 0) {
            return;
        }

        // Only auto-follow while actually playing. When paused/ready, viewport remains stable
        // unless explicitly navigated (e.g., via seek events).
        if (!isPlaying) {
            return;
        }

        double widthSeconds = viewportWidthPixels / (double) pixelsPerSecond;
        double halfWidth = widthSeconds / 2.0;

        // Always keep playhead centered - waveform scrolls underneath
        // This means the viewport starts at (playbackPosition - halfWidth)
        double targetStart = playbackPosition - halfWidth;

        // Allow negative start to show waveform starting from center
        // The waveform renderer should handle negative time ranges appropriately
        startSeconds = targetStart;
    }

    /**
     * Center the viewport on an explicit seek position, even when not playing. This keeps
     * navigation responsive in READY/PAUSED while preserving fire-and-forget replays that should
     * not scroll the viewport.
     */
    @Subscribe
    public void onSeekRequested(@NonNull SeekEvent event) {
        var srOpt = sessionDataSource.getSampleRate();
        if (srOpt.isEmpty() || viewportWidthPixels <= 0 || pixelsPerSecond <= 0) {
            return;
        }
        int sr = srOpt.get();
        double posSeconds = Math.max(0.0, event.frame() / (double) sr);

        double widthSeconds = viewportWidthPixels / (double) pixelsPerSecond;
        double halfWidth = widthSeconds / 2.0;
        startSeconds = posSeconds - halfWidth;
    }

    /** Get the current visible time range. */
    public TimeRange getTimeRange() {
        double widthSeconds = viewportWidthPixels / (double) pixelsPerSecond;
        // Clamp to valid range for TimeRange (which doesn't allow negative values)
        double clampedStart = Math.max(0, startSeconds);
        double clampedEnd = Math.max(0.001, startSeconds + widthSeconds); // Ensure end > start
        return new TimeRange(clampedStart, clampedEnd);
    }

    /**
     * Get the raw viewport start position (can be negative). Used for calculating where to position
     * the waveform.
     */
    public double getRawStartSeconds() {
        return startSeconds;
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
