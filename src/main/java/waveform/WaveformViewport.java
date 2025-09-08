package waveform;

import core.waveform.ScreenDimension;
import java.awt.Graphics2D;

/**
 * Interface for a waveform display viewport. Decouples WaveformPainter from Swing-specific
 * components.
 */
public interface WaveformViewport {

    /** Request a repaint of the viewport. */
    void repaint();

    /**
     * Get the current display bounds.
     *
     * @return Current display bounds (x, y, width, height)
     */
    ScreenDimension getViewportBounds();

    /**
     * Check if the viewport is visible and should be painted.
     *
     * @return true if visible
     */
    boolean isVisible();

    /**
     * Get graphics context for painting.
     *
     * @return Graphics2D for painting, or null if not in paint context
     */
    Graphics2D getPaintGraphics();
}
