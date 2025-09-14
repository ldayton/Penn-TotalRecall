package core.viewport;

import core.audio.session.AudioSessionStateMachine;
import core.viewport.ViewportPaintingDataSource.PaintMode;
import core.waveform.WaveformViewportSpec;
import java.util.Optional;

/**
 * Pure projector API: deterministically maps audio session state and viewport UI state to a
 * viewport rendering projection. Implementations must be side-effect free.
 */
public interface ViewportProjector {

    /** Immutable UI-only state the viewport controls (frame-based). */
    record ViewportUiState(int canvasWidthPx, int canvasHeightPx, double framesPerPixel) {}

    /** Immutable snapshot of audio session state needed for projection (explicit playhead). */
    record AudioSessionSnapshot(
            AudioSessionStateMachine.State state,
            long totalFrames,
            long playheadFrame,
            Optional<String> errorMessage) {}

    /** Result of projecting audio + UI state into a concrete viewport window (frame-based). */
    record Projection(
            PaintMode mode,
            long startFrame,
            long endFrame,
            long generation,
            Optional<String> errorMessage) {}

    /** Compute a deterministic projection for the given audio snapshot and UI state. */
    Projection project(AudioSessionSnapshot audio, ViewportUiState ui);

    /** Convert a Projection to a waveform spec for rendering (seconds-based renderer boundary). */
    WaveformViewportSpec toWaveformViewport(Projection p, ViewportUiState ui, int sampleRate);
}
