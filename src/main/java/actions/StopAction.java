package actions;

import audio.AudioPlayer;
import control.AudioState;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.ActionEvent;

/** Stops audio playback and resets position to the beginning. */
@Singleton
public class StopAction extends BaseAction {

    private final AudioState audioState;

    @Inject
    public StopAction(AudioState audioState) {
        super("Stop", "Stop audio playback and reset to beginning");
        this.audioState = audioState;
    }

    @Override
    protected void performAction(ActionEvent e) {
        if (!audioState.audioOpen()) {
            return;
        }

        boolean currentlyPlaying = audioState.getPlayer().getStatus() == AudioPlayer.Status.PLAYING;
        audioState.getPlayer().stop();
        audioState.setAudioProgressWithoutUpdatingActions(0);
    }

    @Override
    public void update() {
        if (audioState.audioOpen()) {
            if (audioState.getPlayer().getStatus() != AudioPlayer.Status.PLAYING) {
                setEnabled(false);
            } else {
                setEnabled(true);
            }
        } else {
            setEnabled(false);
        }
    }
}
