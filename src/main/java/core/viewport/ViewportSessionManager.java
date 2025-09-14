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

    private final EventDispatchBus eventBus;
    private final AudioSessionDataSource sessionDataSource;
    private final Provider<AudioEngine> audioEngineProvider;
    private final WaveformManager waveformManager;

    // Viewport state
    private final AtomicReference<ViewportContext> currentContext;

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

    // Command history removed; use debug logs for traceability.

    @Override
    public ViewportRenderSpec getRenderSpec(@NonNull ScreenDimension bounds) {
        // Error state takes precedence if present
        var errorOpt = sessionDataSource.getErrorMessage();
        if (errorOpt.isPresent()) {
            return new ViewportRenderSpec(
                    PaintMode.ERROR,
                    errorOpt,
                    java.util.concurrent.CompletableFuture.<java.awt.Image>completedFuture(null),
                    0L);
        }

        // Loading or empty states
        if (sessionDataSource.isLoading()) {
            return new ViewportRenderSpec(
                    PaintMode.LOADING,
                    Optional.empty(),
                    java.util.concurrent.CompletableFuture.<java.awt.Image>completedFuture(null),
                    0L);
        }

        if (!sessionDataSource.isAudioLoaded()) {
            return new ViewportRenderSpec(
                    PaintMode.EMPTY,
                    Optional.empty(),
                    java.util.concurrent.CompletableFuture.<java.awt.Image>completedFuture(null),
                    0L);
        }

        // Ready to render if waveform exists; otherwise still loading
        Waveform waveform = waveformManager.getCurrentWaveform().orElse(null);
        if (waveform == null) {
            return new ViewportRenderSpec(
                    PaintMode.LOADING,
                    Optional.empty(),
                    java.util.concurrent.CompletableFuture.<java.awt.Image>completedFuture(null),
                    0L);
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
                new core.waveform.WaveformViewportSpec(
                        tr.startSeconds(), tr.endSeconds(), bounds.width(), bounds.height(), pps);

        var imageFuture = waveform.renderViewport(wfCtx);

        long generation =
                Double.doubleToLongBits(tr.startSeconds())
                        ^ (Double.doubleToLongBits(tr.endSeconds()) << 1)
                        ^ (((long) bounds.width()) << 32)
                        ^ (((long) bounds.height()) << 16)
                        ^ (pps & 0xFFFF);

        return new ViewportRenderSpec(PaintMode.RENDER, Optional.empty(), imageFuture, generation);
    }
}
