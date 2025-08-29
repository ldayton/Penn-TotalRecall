package actions;

import audio.AudioPlayer;
import components.waveform.WaveformDisplay;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.ActionEvent;
import state.AudioState;

/** Zooms the waveform display out. */
@Singleton
public class ZoomOutAction extends BaseAction {

    private final AudioState audioState;
    private final WaveformDisplay waveformDisplay;

    @Inject
    public ZoomOutAction(AudioState audioState, WaveformDisplay waveformDisplay) {
        super("Zoom Out", "Zoom out the waveform display");
        this.audioState = audioState;
        this.waveformDisplay = waveformDisplay;
    }

    @Override
    protected void performAction(ActionEvent e) {
        waveformDisplay.zoomX(false);
    }

    /** Zooming is enabled only when audio is open and not playing. */
    @Override
    public void update() {
        if (audioState.audioOpen()) {
            setEnabled(audioState.getPlayer().getStatus() != AudioPlayer.Status.PLAYING);
        } else {
            setEnabled(false);
        }
    }
}
