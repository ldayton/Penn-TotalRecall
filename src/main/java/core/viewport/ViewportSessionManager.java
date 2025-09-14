package core.viewport;

import com.google.errorprone.annotations.ThreadSafe;
import com.google.inject.Provider;
import core.audio.AudioEngine;
import core.audio.session.AudioSessionDataSource;
import core.dispatch.EventDispatchBus;
import core.dispatch.Subscribe;
import core.events.AppStateChangedEvent;
import core.waveform.ScreenDimension;
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
    private static final int MAX_COMMAND_HISTORY = 100;

    private final EventDispatchBus eventBus;
    private final AudioSessionDataSource sessionDataSource;
    private final Provider<AudioEngine> audioEngineProvider;
    private final WaveformManager waveformManager;

    // Viewport state
    private final AtomicReference<ViewportContext> currentContext;
    private final ConcurrentLinkedDeque<ViewportCommand> commandHistory;

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
        this.commandHistory = new ConcurrentLinkedDeque<>();
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
        ViewportCommand command =
                new ViewportCommand.PlaybackUpdate(
                        System.currentTimeMillis(), "PLAYBACK", playheadSeconds);
        applyCommand(command);
    }

    public void onUserZoom(int newPixelsPerSecond) {
        if (newPixelsPerSecond <= 0) {
            log.warn("Invalid zoom level: {} pixels per second", newPixelsPerSecond);
            return;
        }
        ViewportCommand command =
                new ViewportCommand.UserZoom(
                        System.currentTimeMillis(), "USER_ZOOM", newPixelsPerSecond);
        applyCommand(command);
    }

    public void onUserSeek(double targetSeconds) {
        ViewportCommand command =
                new ViewportCommand.UserSeek(
                        System.currentTimeMillis(), "USER_SEEK", targetSeconds);
        applyCommand(command);
    }

    public void onCanvasResize(int width, int height) {
        ViewportCommand command =
                new ViewportCommand.CanvasResize(
                        System.currentTimeMillis(), "CANVAS", width, height);
        applyCommand(command);
    }

    private void applyCommand(ViewportCommand command) {
        // Add to history
        commandHistory.addLast(command);
        while (commandHistory.size() > MAX_COMMAND_HISTORY) {
            commandHistory.removeFirst();
        }

        // Update context atomically
        ViewportContext newContext =
                currentContext.updateAndGet(ctx -> computeNewContext(ctx, command));

        if (log.isDebugEnabled()) {
            log.debug(
                    "Viewport command: {} -> playhead={}s, start={}s, end={}s",
                    command.getClass().getSimpleName(),
                    String.format("%.2f", newContext.playheadSeconds()),
                    String.format("%.2f", newContext.getViewportStartTime()),
                    String.format("%.2f", newContext.getViewportEndTime()));
        }
    }

    private ViewportContext computeNewContext(ViewportContext ctx, ViewportCommand command) {
        return switch (command) {
            case ViewportCommand.PlaybackUpdate e ->
                    new ViewportContext(
                            e.playheadSeconds(),
                            ctx.pixelsPerSecond(),
                            ctx.canvasWidth(),
                            ctx.canvasHeight());

            case ViewportCommand.UserZoom e ->
                    new ViewportContext(
                            ctx.playheadSeconds(),
                            e.newPixelsPerSecond(),
                            ctx.canvasWidth(),
                            ctx.canvasHeight());

            case ViewportCommand.UserSeek e ->
                    new ViewportContext(
                            e.targetSeconds(),
                            ctx.pixelsPerSecond(),
                            ctx.canvasWidth(),
                            ctx.canvasHeight());

            case ViewportCommand.CanvasResize e ->
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

    /**
     * Expose current playback position in seconds for tests and diagnostics. Returns 0.0 if no
     * session is active.
     */
    public double getPlaybackPositionSeconds() {
        return currentSession.map(ViewportSession::getPlaybackPositionSeconds).orElse(0.0);
    }

    public String getCommandHistoryDebugString() {
        StringBuilder sb = new StringBuilder("Recent viewport commands (newest first):\n");
        long now = System.currentTimeMillis();

        commandHistory
                .descendingIterator()
                .forEachRemaining(
                        command -> {
                            long ageMs = now - command.timestamp();
                            String details =
                                    switch (command) {
                                        case ViewportCommand.PlaybackUpdate e ->
                                                String.format(
                                                        "playhead=%.2fs", e.playheadSeconds());
                                        case ViewportCommand.UserZoom e ->
                                                String.format(
                                                        "zoom=%dpx/s", e.newPixelsPerSecond());
                                        case ViewportCommand.UserSeek e ->
                                                String.format("seek=%.2fs", e.targetSeconds());
                                        case ViewportCommand.CanvasResize e ->
                                                String.format(
                                                        "size=%dx%d", e.newWidth(), e.newHeight());
                                    };
                            sb.append(
                                    String.format(
                                            "  %4dms ago: %-20s from %-10s [%s]\n",
                                            ageMs,
                                            command.getClass().getSimpleName(),
                                            command.source(),
                                            details));
                        });

        return sb.toString();
    }

    @Override
    public PlayheadAnchoredContext getPlayheadAnchoredContext(@NonNull ScreenDimension bounds) {
        // Error state takes precedence if present
        var errorOpt = sessionDataSource.getErrorMessage();
        if (errorOpt.isPresent()) {
            return new PlayheadAnchoredContext(
                    PaintMode.ERROR, errorOpt, Optional.empty(), 0L, 0.0);
        }

        // Loading or empty states
        if (sessionDataSource.isLoading()) {
            return new PlayheadAnchoredContext(
                    PaintMode.LOADING, Optional.empty(), Optional.empty(), 0L, 0.0);
        }

        if (!sessionDataSource.isAudioLoaded()) {
            return new PlayheadAnchoredContext(
                    PaintMode.EMPTY, Optional.empty(), Optional.empty(), 0L, 0.0);
        }

        // Ready to render if waveform exists; otherwise still loading
        Waveform waveform = waveformManager.getCurrentWaveform().orElse(null);
        if (waveform == null) {
            return new PlayheadAnchoredContext(
                    PaintMode.LOADING, Optional.empty(), Optional.empty(), 0L, 0.0);
        }

        // Build waveform viewport context from a fresh playhead-centric context using current
        // bounds
        var ctx = currentContext.get();
        int pps = ctx.pixelsPerSecond();
        // Recompute time range for provided bounds to ensure playhead is centered at 50%
        var tempCtx =
                new ViewportContext(ctx.playheadSeconds(), pps, bounds.width(), bounds.height());
        TimeRange tr = tempCtx.getTimeRange();
        var wfCtx =
                new core.waveform.ViewportContext(
                        tr.startSeconds(), tr.endSeconds(), bounds.width(), bounds.height(), pps);

        var imageFuture = waveform.renderViewport(wfCtx);

        long playheadFrame = sessionDataSource.getPlaybackPositionFrames().orElse(0L);
        double pixelsPerFrame =
                sessionDataSource
                        .getSampleRate()
                        .map(sr -> sr > 0 ? (pps / (double) sr) : 0.0)
                        .orElse(0.0);

        return new PlayheadAnchoredContext(
                PaintMode.RENDER,
                Optional.empty(),
                Optional.of(imageFuture),
                playheadFrame,
                pixelsPerFrame);
    }
}
