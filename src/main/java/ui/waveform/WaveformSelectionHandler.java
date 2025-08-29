package ui.waveform;

import java.awt.Component;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import javax.swing.SwingUtilities;
import state.AudioState;

/** Mouse handler for waveform selection and audio playback triggering. */
public final class WaveformSelectionHandler implements MouseMotionListener, MouseListener {

    private final Component source;
    private final AudioState audioState;
    private final WaveformCoordinateSystem coordinateSystem;
    private final SelectionOverlay selectionOverlay;

    protected WaveformSelectionHandler(
            Component source,
            AudioState audioState,
            WaveformCoordinateSystem coordinateSystem,
            SelectionOverlay selectionOverlay) {
        this.source = source;
        this.audioState = audioState;
        this.coordinateSystem = coordinateSystem;
        this.selectionOverlay = selectionOverlay;
    }

    /** Start selection highlighting. */
    public void mousePressed(MouseEvent e) {
        var point = e.getPoint();
        selectionOverlay.setHighlightSource(point, source);
        selectionOverlay.setHighlightDest(point, source);
        selectionOverlay.setHighlightMode(true);
        selectionOverlay.repaint();
    }

    /** End selection and play selected audio interval. */
    public void mouseReleased(MouseEvent e) {
        var bounds =
                selectionOverlay.isHighlightMode() ? selectionOverlay.getHighlightBounds() : null;
        selectionOverlay.setHighlightMode(false);
        selectionOverlay.repaint();

        if (bounds != null && audioState.audioOpen()) {
            playSelectedInterval(bounds);
        }
    }

    /** Update selection rectangle during drag. */
    public void mouseDragged(MouseEvent e) {
        selectionOverlay.setHighlightDest(e.getPoint(), source);
        selectionOverlay.repaint();
    }

    /** Play audio interval defined by selection bounds. */
    private void playSelectedInterval(int[] bounds) {
        var startX = Math.max(0, Math.min(bounds[0], bounds[1]));
        var endX =
                Math.min(
                        coordinateSystem.asComponent().getWidth() - 1,
                        Math.max(bounds[0], bounds[1]));

        if (endX > startX) {
            var startPoint = SwingUtilities.convertPoint(selectionOverlay, startX, 0, source);
            var endPoint = SwingUtilities.convertPoint(selectionOverlay, endX, 0, source);
            var startFrame = coordinateSystem.displayXPixelToFrame(startPoint.x);
            var endFrame = coordinateSystem.displayXPixelToFrame(endPoint.x);
            audioState.getPlayer().playShortInterval(startFrame, endFrame);
        }
    }

    // MouseListener/MouseMotionListener interface methods (unused)
    public void mouseMoved(MouseEvent e) {}

    public void mouseClicked(MouseEvent e) {}

    public void mouseEntered(MouseEvent e) {}

    public void mouseExited(MouseEvent e) {}
}
