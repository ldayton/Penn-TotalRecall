package components.waveform;

import actions.ReplayLast200MillisAction;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;

/** Glass pane overlay for mouse selection highlighting and replay flash feedback. */
@Singleton
public final class SelectionOverlay extends JComponent {

    private static final Color MOUSE_HIGHLIGHT_COLOR = UIManager.getColor("Component.focusColor");
    private static final Color REPLAY_FLASH_COLOR = UIManager.getColor("Component.focusColor");

    private final WaveformCoordinateSystem waveformCoordinateSystem;
    private final AlphaComposite composite;

    private volatile boolean highlightMode;
    private volatile Point highlightSource;
    private volatile Point highlightDest;
    private volatile Rectangle highlightRect;

    private Timer timer;

    private volatile boolean flashMode;

    private int flashRectangleXPos;
    private int flashRectangleWidth;

    /** Flash rectangle width in pixels (200ms duration at default zoom). */
    private final int flashWidth =
            (int)
                    (WaveformDisplay.ZOOMLESS_PIXELS_PER_SECOND
                            * (ReplayLast200MillisAction.duration / 1000.0));

    @Inject
    public SelectionOverlay(WaveformCoordinateSystem waveformCoordinateSystem) {
        this.waveformCoordinateSystem = waveformCoordinateSystem;
        this.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25F);
        this.highlightSource = new Point();
        this.highlightDest = new Point();
        this.highlightRect = new Rectangle();
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (highlightMode) {
            paintHighlight((Graphics2D) g);
        } else if (flashMode) {
            paintFlash((Graphics2D) g);
        } else {
            setVisible(false);
        }
    }

    /** Paint mouse selection highlight rectangle. */
    private void paintHighlight(Graphics2D g2d) {
        g2d.setComposite(composite);
        g2d.setColor(MOUSE_HIGHLIGHT_COLOR);
        g2d.fillRect(highlightRect.x, highlightRect.y, highlightRect.width, highlightRect.height);
    }

    /** Paint 200ms replay flash rectangle. */
    private void paintFlash(Graphics2D g2d) {
        g2d.setComposite(composite);
        g2d.setColor(REPLAY_FLASH_COLOR);
        var yPos =
                SwingUtilities.convertPoint(waveformCoordinateSystem.asComponent(), -1, 0, this).y;
        g2d.fillRect(
                flashRectangleXPos,
                yPos,
                flashRectangleWidth,
                waveformCoordinateSystem.getHeight());
    }

    public void setHighlightMode(boolean flag) {
        highlightMode = flag;
        setVisible(flag);
    }

    /** Set highlight start point in overlay coordinates. */
    public void setHighlightSource(Point sourcePoint, Component sourceComp) {
        highlightSource = SwingUtilities.convertPoint(sourceComp, sourcePoint, this);
    }

    /** Set highlight end point and update rectangle bounds. */
    public void setHighlightDest(Point destPoint, Component sourceComp) {
        highlightDest = SwingUtilities.convertPoint(sourceComp, destPoint, this);
        updateHighlightRect();
    }

    /**
     * Update highlight rectangle to span between source and dest points, clipped to waveform
     * bounds.
     */
    private void updateHighlightRect() {
        var minX = Math.min(highlightSource.x, highlightDest.x);
        var maxX = Math.max(highlightSource.x, highlightDest.x);
        var yPos =
                SwingUtilities.convertPoint(waveformCoordinateSystem.asComponent(), 0, 0, this).y;
        var selectionBounds =
                new Rectangle(minX, yPos, maxX - minX, waveformCoordinateSystem.getHeight());
        var waveformBounds =
                SwingUtilities.convertRectangle(
                        waveformCoordinateSystem.asComponent(),
                        waveformCoordinateSystem.getVisibleRect(),
                        this);
        highlightRect = selectionBounds.intersection(waveformBounds);
    }

    /** Get highlight bounds as [sourceX, destX] coordinates. */
    public int[] getHighlightBounds() {
        return new int[] {highlightSource.x, highlightDest.x};
    }

    public boolean isHighlightMode() {
        return highlightMode;
    }

    /** Flash 200ms rectangle at current playback position. */
    public void flashRectangle() {
        if (timer == null || !timer.isRunning()) {
            var flashStartX = waveformCoordinateSystem.getProgressBarXPos() - flashWidth;
            this.flashRectangleXPos =
                    SwingUtilities.convertPoint(
                                    waveformCoordinateSystem.asComponent(), flashStartX, -1, this)
                            .x;
            this.flashRectangleWidth = flashWidth;
            flashMode = true;
            setVisible(true);
            repaint();
            timer =
                    new Timer(
                            ReplayLast200MillisAction.duration,
                            _ -> {
                                flashMode = false;
                                repaint();
                            });
            timer.setRepeats(false);
            timer.start();
        }
    }
}
