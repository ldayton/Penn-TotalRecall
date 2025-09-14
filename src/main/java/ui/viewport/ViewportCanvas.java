package ui.viewport;

import core.waveform.ScreenDimension;
import core.waveform.WaveformViewport;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JComponent;
import lombok.extern.slf4j.Slf4j;

/**
 * A clean canvas for viewport rendering. This component has no internal state about audio timing or
 * position. It simply provides a surface for painting and reports its dimensions.
 */
@Singleton
@Slf4j
public final class ViewportCanvas extends JComponent implements WaveformViewport {

    private final ViewportPainter painter;
    private Graphics2D currentGraphics; // Only valid during paintComponent

    @Inject
    public ViewportCanvas(ViewportPainter painter) {
        this.painter = painter;
        painter.setViewport(this); // Register ourselves as the viewport
        setOpaque(true);
        // Use component's current background from LAF
        setBackground(getBackground());
        setPreferredSize(new Dimension(800, 200));
        log.debug("ViewportCanvas created");
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        // Store graphics for getPaintGraphics() during paint cycle
        currentGraphics = (Graphics2D) g;
        try {
            // Suggest to the painter that now is a good time to paint
            painter.suggestPaint();
        } finally {
            currentGraphics = null;
        }
    }

    // WaveformViewport implementation

    @Override
    public ScreenDimension getViewportBounds() {
        return ScreenDimension.atOrigin(getWidth(), getHeight());
    }

    @Override
    public Graphics2D getPaintGraphics() {
        return currentGraphics;
    }
}
