package core.viewport;

import com.google.errorprone.annotations.ThreadSafe;
import core.audio.session.AudioSessionDataSource;
import core.dispatch.EventDispatchBus;
import core.dispatch.Subscribe;
import core.events.AppStateChangedEvent;
import core.events.ZoomEvent;
import core.waveform.ScreenDimension;
import core.waveform.Waveform;
import core.waveform.WaveformManager;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Optional;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages viewport state and produces render specs for the painter.
 *
 * <p>Thread-safety: This class is annotated as thread-safe because it may be accessed from non-EDT
 * threads (e.g., background event dispatchers, headless mode). While the Swing painter marshals UI
 * work to the EDT, core events can arrive off-EDT, so internal state (e.g., zoom) uses atomics.
 */
@Slf4j
@Singleton
@ThreadSafe
public class ViewportSessionManager implements ViewportPaintingDataSource {

    private final EventDispatchBus eventBus;
    private final AudioSessionDataSource sessionDataSource;
    private final WaveformManager waveformManager;
    private final ViewportProjector projector;

    // Viewport state
    private final java.util.concurrent.atomic.AtomicInteger pixelsPerSecond =
            new java.util.concurrent.atomic.AtomicInteger(200);

    // Session management removed; viewport does not maintain per-audio sessions

    @Inject
    public ViewportSessionManager(
            @NonNull EventDispatchBus eventBus,
            @NonNull AudioSessionDataSource sessionDataSource,
            @NonNull WaveformManager waveformManager,
            @NonNull ViewportProjector projector) {
        this.eventBus = eventBus;
        this.sessionDataSource = sessionDataSource;
        this.waveformManager = waveformManager;
        this.projector = projector;
        eventBus.subscribe(this);
    }

    @Subscribe
    public void onAppStateChanged(@NonNull AppStateChangedEvent event) {
        var newState = event.newState();

        switch (newState) {
            default -> {}
        }
    }

    // getCurrentSession() removed with session elimination

    /**
     * Expose current playback position in seconds for tests and diagnostics. Returns 0.0 if no
     * session is active.
     */
    public double getPlaybackPositionSeconds() {
        return sessionDataSource.getPlaybackPosition().orElse(0.0);
    }

    // Command history removed; use debug logs for traceability.
    /** Adjust zoom level based on user ZoomEvent (IN/OUT). */
    @Subscribe
    public void onZoom(@NonNull ZoomEvent event) {
        int cur = pixelsPerSecond.get();
        double factor = event.direction() == ZoomEvent.Direction.IN ? 1.5 : (1.0 / 1.5);
        int next = Math.max(1, (int) Math.round(cur * factor));
        pixelsPerSecond.set(next);
        log.debug("Zoom {} -> {} px/s", event.direction(), next);
    }

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

        // Build UI + audio snapshots and project
        var sampleRateOpt = sessionDataSource.getSampleRate();
        if (sampleRateOpt.isEmpty()) {
            return new ViewportRenderSpec(
                    PaintMode.LOADING,
                    Optional.empty(),
                    java.util.concurrent.CompletableFuture.<java.awt.Image>completedFuture(null),
                    0L);
        }
        int sampleRate = sampleRateOpt.get();
        double pixelsPerFrame = Math.max(1e-9, pixelsPerSecond.get() / (double) sampleRate);
        ViewportProjector.ViewportUiState uiState =
                new ViewportProjector.ViewportUiState(
                        bounds.width(), bounds.height(), pixelsPerFrame);
        long playheadFrame = sessionDataSource.getPlaybackPositionFrames().orElse(0L);
        long totalFrames = sessionDataSource.getTotalFrames().orElse(0L);
        ViewportProjector.AudioSessionSnapshot audioSnap =
                new ViewportProjector.AudioSessionSnapshot(
                        core.audio.session.AudioSessionStateMachine.State.READY,
                        totalFrames,
                        playheadFrame,
                        Optional.empty());

        var projection = projector.project(audioSnap, uiState);
        var wfCtx = projector.toWaveformViewport(projection, uiState, sampleRate);
        var imageFuture = waveform.renderViewport(wfCtx);
        long generation = projection.generation();

        return new ViewportRenderSpec(PaintMode.RENDER, Optional.empty(), imageFuture, generation);
    }
}
