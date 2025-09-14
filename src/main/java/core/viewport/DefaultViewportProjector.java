package core.viewport;

import core.viewport.ViewportPaintingDataSource.PaintMode;
import core.waveform.WaveformViewportSpec;
import lombok.NonNull;

/**
 * Default, pure implementation of ViewportProjector. Frame-based math with deterministic
 * generation. No side effects.
 */
public class DefaultViewportProjector implements ViewportProjector {

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
            return new Projection(mode, 0L, 0L, 0L, audio.errorMessage());
        }

        long widthFrames = Math.max(1L, Math.round(ui.canvasWidthPx() * ui.pixelsPerFrame()));
        long desiredStart = audio.playheadFrame() - (widthFrames / 2);
        long startFrame = Math.max(0L, desiredStart);
        long endFrame = Math.min(audio.totalFrames(), startFrame + widthFrames);

        long generation =
                (startFrame & 0xFFFFFFFFL)
                        ^ ((endFrame & 0xFFFFFFFFL) << 1)
                        ^ (((long) ui.canvasWidthPx()) << 32)
                        ^ (((long) ui.canvasHeightPx()) << 16)
                        ^ (Double.doubleToLongBits(ui.pixelsPerFrame()));

        return new Projection(
                PaintMode.RENDER, startFrame, endFrame, generation, audio.errorMessage());
    }

    @Override
    public WaveformViewportSpec toWaveformViewport(
            @NonNull Projection p, @NonNull ViewportUiState ui, int sampleRate) {
        double startSeconds = p.startFrame() / (double) sampleRate;
        double endSeconds = p.endFrame() / (double) sampleRate;
        int pixelsPerSecond = Math.max(1, (int) Math.round(ui.pixelsPerFrame() * sampleRate));
        return new WaveformViewportSpec(
                startSeconds, endSeconds, ui.canvasWidthPx(), ui.canvasHeightPx(), pixelsPerSecond);
    }
}
