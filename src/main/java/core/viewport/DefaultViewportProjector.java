package core.viewport;

import core.viewport.ViewportPaintingDataSource.PaintMode;
import core.viewport.smoothing.LinearInterpolationSmoother;
import core.viewport.smoothing.NoSmoother;
import core.viewport.smoothing.PhaseLockedLoopSmoother;
import core.viewport.smoothing.PlayheadSmoother;
import core.viewport.smoothing.PredictiveExtrapolationSmoother;
import core.waveform.WaveformViewportSpec;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Default, pure implementation of ViewportProjector. Frame-based math with deterministic
 * generation. Includes optional playhead smoothing for visual comfort during playback.
 */
@Slf4j
public class DefaultViewportProjector implements ViewportProjector {

    private long generationCounter = 0;
    private final PlayheadSmoother smoother;
    private final String smootherType;
    private long lastUpdateTimeMs = System.currentTimeMillis();

    public DefaultViewportProjector() {
        String configuredType =
                System.getProperty("waveform.smoothing", "predictive-extrapolation");
        this.smootherType = configuredType.toLowerCase();
        this.smoother = createSmoother(smootherType);
        log.info("Initialized waveform smoother: {}", smootherType);
    }

    private static PlayheadSmoother createSmoother(String smoothingType) {
        return switch (smoothingType) {
            case "none" -> new NoSmoother();
            case "linear-interpolation" -> new LinearInterpolationSmoother();
            case "predictive-extrapolation" -> new PredictiveExtrapolationSmoother();
            case "phase-locked-loop" -> new PhaseLockedLoopSmoother();
            default -> {
                log.warn(
                        "Unknown smoother type: {}, defaulting to predictive-extrapolation",
                        smoothingType);
                yield new PredictiveExtrapolationSmoother();
            }
        };
    }

    @Override
    public Projection project(@NonNull AudioSessionSnapshot audio, @NonNull ViewportUiState ui) {
        // Map state to high-level mode first
        PaintMode mode =
                switch (audio.state()) {
                    case NO_AUDIO -> PaintMode.EMPTY;
                    case LOADING -> PaintMode.LOADING;
                    case ERROR -> PaintMode.ERROR;
                    case READY, PLAYING, PAUSED -> PaintMode.RENDER;
                };

        if (mode != PaintMode.RENDER) {
            smoother.reset(); // Reset smoother when not rendering
            return new Projection(mode, 0L, 0L, ++generationCounter, audio.errorMessage());
        }

        // Calculate time delta for smoothing
        long currentTimeMs = System.currentTimeMillis();
        long deltaMs = currentTimeMs - lastUpdateTimeMs;
        lastUpdateTimeMs = currentTimeMs;

        // Apply smoothing to playhead position
        PlayheadSmoother.SmoothingResult smoothingResult =
                smoother.updateAndGetSmoothedPosition(
                        audio.playheadFrame(), deltaMs, audio.state());

        // Log distance from target for debugging
        log.trace(
                "Playhead smoothing [{}]: distance={} frames, playing={}",
                smootherType,
                smoothingResult.distanceFromTarget(),
                audio.state() == core.audio.session.AudioSessionStateMachine.State.PLAYING);

        // frames across the viewport = pixels * (frames/pixel)
        long widthFrames = Math.max(1L, Math.round(ui.canvasWidthPx() * ui.framesPerPixel()));
        long smoothedFrame = smoothingResult.smoothedFrame();
        long startFrame = smoothedFrame - (widthFrames / 2);
        long endFrame = startFrame + widthFrames;

        // Use incrementing counter for generation to detect out-of-order rendering
        long generation = ++generationCounter;

        return new Projection(
                PaintMode.RENDER, startFrame, endFrame, generation, audio.errorMessage());
    }

    @Override
    public WaveformViewportSpec toWaveformViewport(
            @NonNull Projection p, @NonNull ViewportUiState ui, int sampleRate) {
        double startSeconds = p.startFrame() / (double) sampleRate;
        double endSeconds = p.endFrame() / (double) sampleRate;
        // px/s = (px/frame) * (frames/s) = (1 / framesPerPixel) * sampleRate
        int pixelsPerSecond =
                Math.max(1, (int) Math.round((1.0 / ui.framesPerPixel()) * sampleRate));
        return new WaveformViewportSpec(
                startSeconds, endSeconds, ui.canvasWidthPx(), ui.canvasHeightPx(), pixelsPerSecond);
    }
}
