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
     * Immutable viewport render spec: everything the painter needs to draw a frame, frame-based and
     * centered at the playhead.
     *
     * <p>The specId uniquely identifies this spec's content, incorporating the underlying segment
     * cache keys to ensure changes at any layer trigger repaints.
     */
    record ViewportRenderSpec(
            PaintMode mode,
            Optional<String> errorMessage,
            CompletableFuture<Image> image,
            long generation,
            String specId) {}

    /** Build a render spec for the given viewport bounds. */
    ViewportRenderSpec getRenderSpec(ScreenDimension bounds);
}
