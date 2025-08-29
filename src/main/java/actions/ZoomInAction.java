package actions;

import audio.AudioPlayer;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.ActionEvent;
import state.AudioState;
import ui.waveform.WaveformDisplay;

/** Zooms the waveform display in. */
@Singleton
public class ZoomInAction extends BaseAction {

    private final AudioState audioState;
    private final WaveformDisplay waveformDisplay;

    @Inject
    public ZoomInAction(AudioState audioState, WaveformDisplay waveformDisplay) {
        super("Zoom In", "Zoom in the waveform display");
        this.audioState = audioState;
        this.waveformDisplay = waveformDisplay;
    }

    @Override
    protected void performAction(ActionEvent e) {
        waveformDisplay.zoomX(true);
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
