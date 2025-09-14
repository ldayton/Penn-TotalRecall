package core.viewport;

import com.google.errorprone.annotations.ThreadSafe;
import core.audio.session.AudioSessionDataSource;
import core.audio.session.AudioSessionStateMachine;
import core.dispatch.EventDispatchBus;
import core.dispatch.Subscribe;
import core.events.ZoomEvent;
import core.waveform.ScreenDimension;
import core.waveform.Waveform;
import core.waveform.WaveformManager;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.Image;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
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
    private final AudioSessionDataSource sessionDataSource;
    private final WaveformManager waveformManager;
    private final ViewportProjector projector;

    // Zoom stored as frames-per-pixel (frame-based, sample-rate agnostic)
    private static final double MIN_FRAMES_PER_PIXEL = 1e-9;
    private static final double MAX_FRAMES_PER_PIXEL = 1e9;
    private final AtomicReference<Double> framesPerPixel = new AtomicReference<>(10.0);

    @Inject
    public ViewportSessionManager(
            @NonNull EventDispatchBus eventBus,
            @NonNull AudioSessionDataSource sessionDataSource,
            @NonNull WaveformManager waveformManager,
            @NonNull ViewportProjector projector) {
        this.sessionDataSource = sessionDataSource;
        this.waveformManager = waveformManager;
        this.projector = projector;
        eventBus.subscribe(this);
    }

    // Command history removed; use debug logs for traceability.
    /** Adjust zoom level based on user ZoomEvent (IN/OUT). */
    @Subscribe
    public void onZoom(@NonNull ZoomEvent event) {
        double cur = framesPerPixel.get();
        double next = event.direction() == ZoomEvent.Direction.IN ? (cur / 1.5) : (cur * 1.5);
        if (next < MIN_FRAMES_PER_PIXEL) next = MIN_FRAMES_PER_PIXEL;
        if (next > MAX_FRAMES_PER_PIXEL) next = MAX_FRAMES_PER_PIXEL;
        framesPerPixel.set(next);
        log.debug("Zoom {} -> {} frames/pixel", event.direction(), next);
    }

    @Override
    public ViewportRenderSpec getRenderSpec(@NonNull ScreenDimension bounds) {
        // Loading or empty states
        var snap = sessionDataSource.snapshot();
        if (snap.state() == AudioSessionStateMachine.State.ERROR) {
            return new ViewportRenderSpec(
                    PaintMode.ERROR,
                    snap.errorMessage(),
                    CompletableFuture.<Image>completedFuture(null),
                    0L);
        }
        if (snap.state() == AudioSessionStateMachine.State.LOADING) {
            return new ViewportRenderSpec(
                    PaintMode.LOADING,
                    Optional.empty(),
                    CompletableFuture.<Image>completedFuture(null),
                    0L);
        }

        if (snap.state() == AudioSessionStateMachine.State.NO_AUDIO) {
            return new ViewportRenderSpec(
                    PaintMode.EMPTY,
                    Optional.empty(),
                    CompletableFuture.<Image>completedFuture(null),
                    0L);
        }

        // Ready to render if waveform exists; otherwise still loading
        Waveform waveform = waveformManager.getCurrentWaveform().orElse(null);
        if (waveform == null) {
            return new ViewportRenderSpec(
                    PaintMode.LOADING,
                    Optional.empty(),
                    CompletableFuture.<Image>completedFuture(null),
                    0L);
        }

        // Build UI + audio snapshots and project
        var audioSnapshot = sessionDataSource.snapshot();
        int sampleRate = audioSnapshot.sampleRate();
        if (sampleRate == 0) {
            return new ViewportRenderSpec(
                    PaintMode.LOADING,
                    Optional.empty(),
                    CompletableFuture.<Image>completedFuture(null),
                    0L);
        }
        double fpp =
                Math.max(
                        MIN_FRAMES_PER_PIXEL, Math.min(MAX_FRAMES_PER_PIXEL, framesPerPixel.get()));
        ViewportProjector.ViewportUiState uiState =
                new ViewportProjector.ViewportUiState(bounds.width(), bounds.height(), fpp);
        long playheadFrame = snap.playheadFrame();
        long totalFrames = snap.totalFrames();
        ViewportProjector.AudioSessionSnapshot audioSnap =
                new ViewportProjector.AudioSessionSnapshot(
                        snap.state(), totalFrames, playheadFrame, snap.errorMessage());

        var projection = projector.project(audioSnap, uiState);
        var wfCtx = projector.toWaveformViewport(projection, uiState, sampleRate);
        var imageFuture = waveform.renderViewport(wfCtx);
        long generation = projection.generation();

        return new ViewportRenderSpec(PaintMode.RENDER, Optional.empty(), imageFuture, generation);
    }
}
