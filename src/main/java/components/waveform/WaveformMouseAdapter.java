package components.waveform;

import control.AudioState;
import java.awt.Component;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import javax.swing.SwingUtilities;

/** Mouse adapter for the waveform display, for use when {@link info.Constants#mouseMode}. */
public class WaveformMouseAdapter implements MouseMotionListener, MouseListener {

    private final Component source;
    private final AudioState audioState;

    protected WaveformMouseAdapter(Component source, AudioState audioState) {
        this.source = source;
        this.audioState = audioState;
    }

    //	@Override
    //	public void mouseClicked(MouseEvent e) {
    //		if(e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
    //			if(CurAudio.audioOpen()) {
    //				if(CurAudio.getPlayer().getStatus() !=AudioPlayer.Status.PLAYING) {
    ////					System.out.println("jump");
    //				}
    //			}
    //		}
    //	}

    public void mousePressed(MouseEvent e) {
        SelectionOverlay.getInstance().setHighlightSource(e.getPoint(), source);
        SelectionOverlay.getInstance().setHighlightDest(e.getPoint(), source);
        SelectionOverlay.getInstance().setHighlightMode(true);
        SelectionOverlay.getInstance().repaint();
    }

    public void mouseReleased(MouseEvent e) {
        int[] xs = null;
        if (SelectionOverlay.getInstance().isHighlightMode()) {
            xs = SelectionOverlay.getInstance().getHighlightBounds();
        }
        SelectionOverlay.getInstance().setHighlightMode(false);
        SelectionOverlay.getInstance().repaint();
        if (xs == null) {
            return;
        }
        if (audioState.audioOpen()) {
            int smallerX = Math.min(xs[0], xs[1]);
            smallerX = Math.max(0, smallerX);
            int largerX = Math.max(xs[0], xs[1]);
            largerX = Math.min(largerX, WaveformDisplay.getInstance().getWidth() - 1);
            if (largerX <= smallerX) {
                return;
            }
            Point firstPoint =
                    SwingUtilities.convertPoint(
                            SelectionOverlay.getInstance(), smallerX, 0, source);
            Point secondPoint =
                    SwingUtilities.convertPoint(SelectionOverlay.getInstance(), largerX, 0, source);
            audioState
                    .getPlayer()
                    .playShortInterval(
                            WaveformDisplay.displayXPixelToFrame((int) firstPoint.getX()),
                            WaveformDisplay.displayXPixelToFrame((int) secondPoint.getX()));
        }
    }

    public void mouseDragged(MouseEvent e) {
        SelectionOverlay.getInstance().setHighlightDest(e.getPoint(), source);
        SelectionOverlay.getInstance().repaint();
    }

    public void mouseMoved(MouseEvent e) {}

    public void mouseClicked(MouseEvent e) {}

    public void mouseEntered(MouseEvent e) {}

    public void mouseExited(MouseEvent e) {}
}
