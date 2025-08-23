package actions;

import audio.AudioPlayer;
import components.waveform.WaveformDisplay;
import control.AudioState;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.ActionEvent;

/**
 * Zooms the waveform display in/out.
 *
 * <p>It only changes the pixelsPerSecond value that the waveform display will utilize on its next
 * repaint.
 */
@Singleton
public class ZoomAction extends BaseAction {

    private final AudioState audioState;
    private final boolean zoomIn;

    @Inject
    public ZoomAction(AudioState audioState) {
        super("Zoom", "Zoom waveform display");
        this.audioState = audioState;
        this.zoomIn = true; // Default to zoom in
    }

    /**
     * Performs the zoom, increasing/decreasing the pixelsPerSecond by calling {@link
     * components.waveform.WaveformDisplay#zoomX(boolean)}.
     *
     * <p>Since the waveform display autonomously decides when to paint itself, this action may not
     * result in an instant visual change.
     */
    @Override
    protected void performAction(ActionEvent e) {
        if (zoomIn) {
            WaveformDisplay.zoomX(true);
        } else {
            WaveformDisplay.zoomX(false);
        }
    }

    /** Zooming is enabled only when audio is open and not playing. */
    @Override
    public void update() {
        if (audioState.audioOpen()) {
            if (audioState.getPlayer().getStatus() == AudioPlayer.Status.PLAYING) {
                setEnabled(false);
            } else {
                setEnabled(true);
            }
        } else {
            setEnabled(false);
        }
    }
}
