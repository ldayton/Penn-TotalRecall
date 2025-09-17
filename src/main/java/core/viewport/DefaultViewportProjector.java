package core.viewport;

import core.viewport.ViewportPaintingDataSource.PaintMode;
import core.viewport.smoothing.MetricsAwarePlayheadSmoother;
import core.viewport.smoothing.PhaseLockedLoopSmoother;
import core.viewport.smoothing.PlayheadSmoother;
import core.viewport.smoothing.SmoothingMetrics;
import core.waveform.WaveformViewportSpec;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Default, pure implementation of ViewportProjector. Frame-based math with deterministic
 * generation. Includes optional playhead smoothing for visual comfort during playback.
 */
@Slf4j
public class DefaultViewportProjector implements ViewportProjector {

    private static final int RENDER_FPS = 60; // Target render frame rate
    private long generationCounter = 0;
    private final MetricsAwarePlayheadSmoother smoother;
    private final SmoothingMetrics renderMetrics; // Separate metrics for render-rate sampling
    private long lastUpdateTimeMs = System.currentTimeMillis();
    private long lastRenderedFrame = 0; // Track last rendered position for metrics
    private long frameCounter = 0;
    private static final long REPORT_INTERVAL = 500; // Report metrics every 500 frames

    public DefaultViewportProjector() {
        // Choose which smoother to use
        PlayheadSmoother baseSmoother = createSmoother();
        this.smoother = new MetricsAwarePlayheadSmoother(baseSmoother, 100);
        this.renderMetrics =
                new SmoothingMetrics(RENDER_FPS * 5); // 5 seconds of samples at actual FPS
        log.info(
                "Initialized {} with render-rate metrics at {}fps",
                baseSmoother.getClass().getSimpleName(),
                RENDER_FPS);
    }

    private PlayheadSmoother createSmoother() {
        // Toggle between smoothers by changing this line:
        // return new NoSmoother();  // Option 1: No smoothing (baseline)
        // return new LinearInterpolationSmoother();  // Option 2: Linear interpolation
        return new PhaseLockedLoopSmoother(); // Option 3: Phase-locked loop
        // return new PredictiveExtrapolationSmoother();  // Option 4: Predictive extrapolation
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
            renderMetrics.reset(); // Reset render metrics too
            lastRenderedFrame = 0; // Reset position tracking
            return new Projection(mode, 0L, 0L, ++generationCounter, audio.errorMessage());
        }

        // Calculate time delta for smoothing
        long currentTimeMs = System.currentTimeMillis();
        long deltaMs = currentTimeMs - lastUpdateTimeMs;
        lastUpdateTimeMs = currentTimeMs;

        // Update smoother and get smoothed position
        PlayheadSmoother.SmoothingResult smoothingResult =
                smoother.updateAndGetSmoothedPosition(
                        audio.playheadFrame(), deltaMs, audio.state());

        // Collect render-rate metrics (every frame at display FPS)
        // We measure smoothness by tracking how the rendered position changes frame-to-frame
        // NOT by comparing to audio position (which updates infrequently)
        if (audio.state() == core.audio.session.AudioSessionStateMachine.State.PLAYING) {
            // Use a synthetic "target" that represents ideal linear progression
            // This lets us measure the actual visual smoothness
            long expectedFrame =
                    lastRenderedFrame + (long) (deltaMs * 44.1); // 44.1 frames/ms at 44100Hz
            renderMetrics.addSample(currentTimeMs, smoothingResult.smoothedFrame(), expectedFrame);
            lastRenderedFrame = smoothingResult.smoothedFrame();
        } else {
            // When paused or stopped, update position without collecting metrics
            lastRenderedFrame = smoothingResult.smoothedFrame();
        }

        // Log metrics periodically
        frameCounter++;
        if (frameCounter % REPORT_INTERVAL == 0) {
            SmoothingMetrics.SmoothnessScores scores = renderMetrics.calculateScores();
            if (scores != null) {
                // Get smoother name and abbreviate it
                String fullName = smoother.getDelegate().getClass().getSimpleName();
                String smootherName =
                        switch (fullName) {
                            case "NoSmoother" -> "NO";
                            case "LinearInterpolationSmoother" -> "LI";
                            case "PhaseLockedLoopSmoother" -> "PLL";
                            case "PredictiveExtrapolationSmoother" -> "PE";
                            default -> fullName;
                        };

                // Build dynamic header
                String header =
                        String.format(
                                "%s Smoothing @ %dfps (frame %d)",
                                smootherName, RENDER_FPS, frameCounter);

                // Calculate padding to center the header (50 chars total inside box)
                int totalInnerWidth = 50;
                int headerLength = header.length();
                int totalPadding = Math.max(0, totalInnerWidth - headerLength);
                int leftPadding = totalPadding / 2;
                int rightPadding = totalPadding - leftPadding;

                // Build table as single string for cleaner output
                StringBuilder table = new StringBuilder();
                table.append("\n╔══════════════════════════════════════════════════╗\n");
                table.append(
                        String.format(
                                "║%s%s%s║\n",
                                " ".repeat(leftPadding), header, " ".repeat(rightPadding)));
                table.append("╠════════════════════╦═════════════════════════════╣\n");
                table.append(
                        String.format("║ %-18s ║ %27.2f ║\n", "SPARC Score", scores.sparcScore()));
                table.append(String.format("║ %-18s ║ %27.4f ║\n", "Jerk RMS", scores.jerkRMS()));
                table.append(String.format("║ %-18s ║ %24.2f ms ║\n", "Avg Lag", scores.lagMs()));
                table.append(
                        String.format("║ %-18s ║ %24.2f ms ║\n", "P95 Lag", scores.p95LagMs()));
                table.append(
                        String.format("║ %-18s ║ %24.2f ms ║\n", "P99 Lag", scores.p99LagMs()));
                table.append(
                        String.format("║ %-18s ║ %24.2f ms ║\n", "Max Lag", scores.maxLagMs()));
                table.append(
                        String.format("║ %-18s ║ %25.1f %% ║\n", "Overshoot", scores.overshoot()));
                table.append("╚════════════════════╩═════════════════════════════╝");
                log.info(table.toString());
            }
        }

        // Log distance from target for debugging
        log.trace(
                "Playhead smoothing: distance={} frames, playing={}",
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
            @NonNull Projection p,
            @NonNull ViewportUiState ui,
            @NonNull AudioSessionSnapshot audio,
            int sampleRate) {
        double startSeconds = p.startFrame() / (double) sampleRate;
        double endSeconds = p.endFrame() / (double) sampleRate;
        double audioDurationSeconds = audio.totalFrames() / (double) sampleRate;
        // px/s = (px/frame) * (frames/s) = (1 / framesPerPixel) * sampleRate
        int pixelsPerSecond =
                Math.max(1, (int) Math.round((1.0 / ui.framesPerPixel()) * sampleRate));
        return new WaveformViewportSpec(
                startSeconds,
                endSeconds,
                ui.canvasWidthPx(),
                ui.canvasHeightPx(),
                pixelsPerSecond,
                audioDurationSeconds);
    }
}
