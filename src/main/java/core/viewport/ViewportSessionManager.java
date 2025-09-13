package core.viewport;

import com.google.errorprone.annotations.ThreadSafe;
import com.google.inject.Provider;
import core.audio.AudioEngine;
import core.audio.AudioSessionDataSource;
import core.dispatch.EventDispatchBus;
import core.dispatch.Subscribe;
import core.events.AppStateChangedEvent;
import core.waveform.TimeRange;
import core.waveform.Waveform;
import core.waveform.WaveformManager;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicReference;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages viewport state and ViewportSession lifecycle. Combines state management with session
 * lifecycle management.
 */
@Slf4j
@Singleton
@ThreadSafe
public class ViewportSessionManager implements ViewportPaintingDataSource {
    private static final int MAX_EVENT_HISTORY = 100;

    private final EventDispatchBus eventBus;
    private final AudioSessionDataSource sessionDataSource;
    private final Provider<AudioEngine> audioEngineProvider;
    private final WaveformManager waveformManager;

    // Viewport state
    private final AtomicReference<ViewportContext> currentContext;
    private final ConcurrentLinkedDeque<ViewportEvent> eventHistory;

    // Session management
    private Optional<ViewportSession> currentSession = Optional.empty();

    @Inject
    public ViewportSessionManager(
            @NonNull EventDispatchBus eventBus,
            @NonNull AudioSessionDataSource sessionDataSource,
            @NonNull Provider<AudioEngine> audioEngineProvider,
            @NonNull WaveformManager waveformManager) {
        this.eventBus = eventBus;
        this.sessionDataSource = sessionDataSource;
        this.audioEngineProvider = audioEngineProvider;
        this.waveformManager = waveformManager;
        this.currentContext = new AtomicReference<>(ViewportContext.createDefault());
        this.eventHistory = new ConcurrentLinkedDeque<>();
        eventBus.subscribe(this);
    }

    @Subscribe
    public void onAppStateChanged(@NonNull AppStateChangedEvent event) {
        var newState = event.newState();

        switch (newState) {
            case READY -> {
                // Audio loaded, create viewport session
                sessionDataSource
                        .getSampleRate()
                        .ifPresent(
                                sampleRate -> {
                                    // Dispose old session if exists
                                    currentSession.ifPresent(
                                            session -> session.dispose(audioEngineProvider));

                                    // Create new session with sample rate
                                    var session =
                                            new ViewportSession(
                                                    eventBus,
                                                    this,
                                                    audioEngineProvider,
                                                    sampleRate);
                                    currentSession = Optional.of(session);

                                    log.debug(
                                            "Created ViewportSession for sample rate: {} Hz",
                                            sampleRate);
                                });
            }

            case NO_AUDIO -> {
                // Audio closed, dispose viewport session
                currentSession.ifPresent(
                        session -> {
                            session.dispose(audioEngineProvider);
                            log.debug("Disposed ViewportSession");
                        });
                currentSession = Optional.empty();
            }

            default -> {
                // Other states don't affect viewport lifecycle
            }
        }
    }

    // ViewportManager interface methods

    public void onPlaybackUpdate(double playheadSeconds) {
        ViewportEvent event =
                new ViewportEvent.PlaybackUpdateEvent(
                        System.currentTimeMillis(), "PLAYBACK", playheadSeconds);
        applyEvent(event);
    }

    public void onUserZoom(int newPixelsPerSecond) {
        if (newPixelsPerSecond <= 0) {
            log.warn("Invalid zoom level: {} pixels per second", newPixelsPerSecond);
            return;
        }
        ViewportEvent event =
                new ViewportEvent.UserZoomEvent(
                        System.currentTimeMillis(), "USER_ZOOM", newPixelsPerSecond);
        applyEvent(event);
    }

    public void onUserSeek(double targetSeconds) {
        ViewportEvent event =
                new ViewportEvent.UserSeekEvent(
                        System.currentTimeMillis(), "USER_SEEK", targetSeconds);
        applyEvent(event);
    }

    public void onCanvasResize(int width, int height) {
        ViewportEvent event =
                new ViewportEvent.CanvasResizeEvent(
                        System.currentTimeMillis(), "CANVAS", width, height);
        applyEvent(event);
    }

    private void applyEvent(ViewportEvent event) {
        // Add to history
        eventHistory.addLast(event);
        while (eventHistory.size() > MAX_EVENT_HISTORY) {
            eventHistory.removeFirst();
        }

        // Update context atomically
        ViewportContext newContext =
                currentContext.updateAndGet(ctx -> computeNewContext(ctx, event));

        if (log.isDebugEnabled()) {
            log.debug(
                    "Viewport event: {} -> playhead={}s, start={}s, end={}s",
                    event.getClass().getSimpleName(),
                    String.format("%.2f", newContext.playheadSeconds()),
                    String.format("%.2f", newContext.getViewportStartTime()),
                    String.format("%.2f", newContext.getViewportEndTime()));
        }
    }

    private ViewportContext computeNewContext(ViewportContext ctx, ViewportEvent event) {
        return switch (event) {
            case ViewportEvent.PlaybackUpdateEvent e ->
                    new ViewportContext(
                            e.playheadSeconds(),
                            ctx.pixelsPerSecond(),
                            ctx.canvasWidth(),
                            ctx.canvasHeight());

            case ViewportEvent.UserZoomEvent e ->
                    new ViewportContext(
                            ctx.playheadSeconds(),
                            e.newPixelsPerSecond(),
                            ctx.canvasWidth(),
                            ctx.canvasHeight());

            case ViewportEvent.UserSeekEvent e ->
                    new ViewportContext(
                            e.targetSeconds(),
                            ctx.pixelsPerSecond(),
                            ctx.canvasWidth(),
                            ctx.canvasHeight());

            case ViewportEvent.CanvasResizeEvent e ->
                    new ViewportContext(
                            ctx.playheadSeconds(),
                            ctx.pixelsPerSecond(),
                            e.newWidth(),
                            e.newHeight());
        };
    }

    public ViewportContext getContext() {
        return currentContext.get();
    }

    /** Get the current viewport session if one exists. */
    public Optional<ViewportSession> getCurrentSession() {
        return currentSession;
    }

    public String getEventHistoryDebugString() {
        StringBuilder sb = new StringBuilder("Recent viewport events (newest first):\n");
        long now = System.currentTimeMillis();

        eventHistory
                .descendingIterator()
                .forEachRemaining(
                        event -> {
                            long ageMs = now - event.timestamp();
                            String details =
                                    switch (event) {
                                        case ViewportEvent.PlaybackUpdateEvent e ->
                                                String.format(
                                                        "playhead=%.2fs", e.playheadSeconds());
                                        case ViewportEvent.UserZoomEvent e ->
                                                String.format(
                                                        "zoom=%dpx/s", e.newPixelsPerSecond());
                                        case ViewportEvent.UserSeekEvent e ->
                                                String.format("seek=%.2fs", e.targetSeconds());
                                        case ViewportEvent.CanvasResizeEvent e ->
                                                String.format(
                                                        "size=%dx%d", e.newWidth(), e.newHeight());
                                    };
                            sb.append(
                                    String.format(
                                            "  %4dms ago: %-20s from %-10s [%s]\n",
                                            ageMs,
                                            event.getClass().getSimpleName(),
                                            event.source(),
                                            details));
                        });

        return sb.toString();
    }

    // ViewportPaintingDataSource implementation

    @Override
    public TimeRange getTimeRange() {
        // Return null if no audio loaded (no valid time range)
        if (!sessionDataSource.isAudioLoaded()) {
            return null;
        }
        return currentContext.get().getTimeRange();
    }

    @Override
    public int getPixelsPerSecond() {
        return currentContext.get().pixelsPerSecond();
    }

    @Override
    public double getPlaybackPositionSeconds() {
        // Get playback position from the session's playback listener if available
        return currentSession.map(ViewportSession::getPlaybackPositionSeconds).orElse(0.0);
    }

    @Override
    public Waveform getWaveform() {
        return waveformManager.getCurrentWaveform().orElse(null);
    }

    @Override
    public boolean isPlaying() {
        return currentSession.map(ViewportSession::isPlaying).orElse(false);
    }
}
