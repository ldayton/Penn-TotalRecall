package behaviors.multiact;

import audio.PrecisionPlayer;
import components.waveform.WaveformDisplay;
import control.CurAudio;
import java.awt.event.ActionEvent;

/**
 * Zooms the waveform display in/out.
 *
 * <p>It only changes the pixelsPerSecond value that the waveform display will utilize on its next
 * repaint.
 */
public class ZoomAction extends IdentifiedMultiAction {

    /** Defines the zoom direction of a <code>ZoomAction</code> instance. */
    public enum Direction {
        IN,
        OUT
    }

    private final Direction dir;

    public ZoomAction(Direction dir) {
        super(dir);
        this.dir = dir;
    }

    /**
     * Performs the zoom, increasing/decreasing the pixelsPerSecond by calling {@link
     * components.waveform.WaveformDisplay#zoomX(boolean)}.
     *
     * <p>Since the waveform display autonomously decides when to paint itself, this action may not
     * result in an instant visual change.
     *
     * @param e The <code>ActionEvent</code> provided by the trigger
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        super.actionPerformed(e);
        if (dir == Direction.IN) {
            WaveformDisplay.zoomX(true);
        } else {
            WaveformDisplay.zoomX(false);
        }
    }

    /** Zooming is enabled only when audio is open and not playing. */
    @Override
    public void update() {
        if (CurAudio.audioOpen()) {
            if (CurAudio.getPlayer().getStatus() == PrecisionPlayer.Status.PLAYING) {
                setEnabled(false);
            } else {
                setEnabled(true);
            }
        } else {
            setEnabled(false);
        }
    }
}
