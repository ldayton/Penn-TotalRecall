package s2;

import w2.ViewportContext;

/**
 * Interface for a waveform display viewport. Decouples WaveformPainter from Swing-specific
 * components.
 */
public interface WaveformViewport {

    /** Request a repaint of the viewport. */
    void repaint();

    /**
     * Get the current viewport context (scroll position, zoom, size).
     *
     * @return Current viewport context
     */
    ViewportContext getViewportContext();

    /**
     * Check if the viewport is visible and should be painted.
     *
     * @return true if visible
     */
    boolean isVisible();
}
