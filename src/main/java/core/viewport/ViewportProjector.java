package core.viewport;

import core.audio.session.AudioSessionStateMachine;
import core.viewport.ViewportPaintingDataSource.PaintMode;
import core.waveform.ScreenDimension;
import core.waveform.WaveformViewportSpec;
import java.util.Optional;

/**
 * Pure projector API: deterministically maps audio session state and viewport UI state to a
 * viewport rendering projection. Implementations must be side-effect free.
 */
public interface ViewportProjector {

    /** Immutable UI-only state the viewport controls. */
    record ViewportUiState(int canvasWidthPx, int canvasHeightPx, int pixelsPerSecond) {}

    /**
     * Immutable snapshot of audio session state needed for projection. currentFrame is present when
     * playing/paused; pendingStartFrame is used in READY state.
     */
    record AudioSessionSnapshot(
            AudioSessionStateMachine.State state,
            long totalFrames,
            int sampleRate,
            Optional<Long> currentFrame,
            Optional<Long> pendingStartFrame,
            Optional<String> errorMessage) {}

    /**
     * Result of projecting audio + UI state into a concrete viewport window. The projector computes
     * both the frame window and pixel-space padding required to keep the playhead centered when
     * near the start of the file.
     */
    record Projection(
            PaintMode mode,
            long playheadFrame,
            long startFrame,
            long endFrame,
            int leftPadPixels,
            double pixelsPerFrame,
            Optional<String> errorMessage) {}

    /** Compute a deterministic projection for the given audio snapshot and UI state. */
    Projection project(AudioSessionSnapshot audio, ViewportUiState ui);

    /**
     * Convert a Projection to a waveform ViewportContext for rendering. Implementations may choose
     * how to map frames to seconds internally.
     */
    WaveformViewportSpec toWaveformViewport(Projection p, ScreenDimension bounds, int sampleRate);
}
