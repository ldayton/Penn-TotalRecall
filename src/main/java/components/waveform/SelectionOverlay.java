package components.waveform;

import actions.ReplayLast200MillisAction;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.AlphaComposite;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import ui.UiColors;
import ui.UiConstants;

/** Application glass pane, used for drawing mouse feedback. */
@Singleton
public class SelectionOverlay extends JComponent {

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

    private final int flashWidth =
            (int)
                    (UiConstants.zoomlessPixelsPerSecond
                            * (ReplayLast200MillisAction.duration / (double) 1000));

    @Inject
    public SelectionOverlay(WaveformCoordinateSystem waveformCoordinateSystem) {
        this.waveformCoordinateSystem = waveformCoordinateSystem;
        composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25F);
        flashMode = false;
        highlightMode = false;
        highlightSource = new Point();
        highlightDest = new Point();
        highlightRect = new Rectangle();
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (highlightMode) {
            Graphics2D g2d = (Graphics2D) g;
            g2d.setComposite(composite);
            g2d.setColor(UiColors.mouseHighlightColor);
            g2d.fillRect(
                    (int) highlightRect.getX(),
                    (int) highlightRect.getY(),
                    (int) highlightRect.getWidth(),
                    (int) highlightRect.getHeight());
        } else if (flashMode) {
            Graphics2D g2d = (Graphics2D) g;
            g2d.setComposite(composite);
            g2d.setColor(UiColors.replay200MillisFlashColor);
            int yPos =
                    (int)
                            SwingUtilities.convertPoint(
                                            waveformCoordinateSystem.asComponent(), -1, 0, this)
                                    .getY();
            g2d.fillRect(
                    flashRectangleXPos,
                    yPos,
                    flashRectangleWidth,
                    waveformCoordinateSystem.getHeight());
        } else {
            setVisible(false);
        }
    }

    public void setHighlightMode(boolean flag) {
        highlightMode = flag;
        setVisible(flag);
    }

    public void setHighlightSource(Point sourcePoint, Component sourceComp) {
        highlightSource = SwingUtilities.convertPoint(sourceComp, sourcePoint, this);
    }

    public void setHighlightDest(Point destPoint, Component sourceComp) {
        highlightDest = SwingUtilities.convertPoint(sourceComp, destPoint, this);
        udpateHighlightRect();
    }

    private void udpateHighlightRect() {
        int xSource =
                (int)
                        (highlightSource.getX() < highlightDest.getX()
                                ? highlightSource.getX()
                                : highlightDest.getX());
        int ySource =
                (int)
                        SwingUtilities.convertPoint(
                                        waveformCoordinateSystem.asComponent(), 0, 0, this)
                                .getY();
        int width = (int) Math.abs(highlightSource.getX() - highlightDest.getX());
        int height = waveformCoordinateSystem.getHeight();
        Rectangle naiveBounds = new Rectangle(xSource, ySource, width, height);
        Rectangle waveformBounds =
                SwingUtilities.convertRectangle(
                        waveformCoordinateSystem.asComponent(),
                        waveformCoordinateSystem.getVisibleRect(),
                        this);
        highlightRect = naiveBounds.intersection(waveformBounds);
    }

    public int[] getHighlightBounds() {
        return new int[] {(int) highlightSource.getX(), (int) highlightDest.getX()};
    }

    public boolean isHighlightMode() {
        return highlightMode;
    }

    public void flashRectangle() {
        if ((timer != null && timer.isRunning()) == false) {
            this.flashRectangleXPos =
                    (int)
                            SwingUtilities.convertPoint(
                                            waveformCoordinateSystem.asComponent(),
                                            waveformCoordinateSystem.getProgressBarXPos()
                                                    - flashWidth,
                                            -1,
                                            this)
                                    .getX();
            this.flashRectangleWidth = flashWidth;
            flashMode = true;
            setVisible(true);
            repaint();
            timer = new Timer(ReplayLast200MillisAction.duration, new StopFlashListener());
            timer.setRepeats(false);
            timer.start();
        }
    }

    private final class StopFlashListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            flashMode = false;
            repaint();
        }
    }
}
