package core.viewport;

import core.waveform.ScreenDimension;
import java.awt.Image;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Data source for viewport painting operations (new, playhead-anchored API).
 *
 * <p>This interface provides a single, immutable, frame-based context for painting. The returned
 * image is pre-aligned so the absolute playhead frame maps to the horizontal center (50%) of the
 * viewport. The painter simply draws the image to the full viewport bounds and draws the playhead
 * at the center, without additional layout logic.
 */
public interface ViewportPaintingDataSource {

    /** High-level mode indicating what the painter should draw. */
    enum PaintMode {
        EMPTY, // No audio loaded
        LOADING, // Audio or waveform loading
        ERROR, // Error state with message
        RENDER // Ready to render waveform image
    }

    /**
     * Immutable, playhead-anchored, frame-based context.
     *
     * @param mode High-level instruction for what to draw.
     * @param errorMessage Error text when {@code mode == ERROR}; empty otherwise.
     * @param image Future for the waveform image sized to the viewport when {@code mode == RENDER};
     *     empty otherwise.
     * @param playheadFrame Absolute audio frame at the playhead.
     * @param pixelsPerFrame Effective zoom for overlays/ticks, in pixels per audio frame.
     */
    record PlayheadAnchoredContext(
            PaintMode mode,
            Optional<String> errorMessage,
            Optional<CompletableFuture<Image>> image,
            long playheadFrame,
            double pixelsPerFrame) {}

    /** Build a playhead-anchored, frame-based painting context for the given viewport bounds. */
    PlayheadAnchoredContext getPlayheadAnchoredContext(ScreenDimension bounds);
}
