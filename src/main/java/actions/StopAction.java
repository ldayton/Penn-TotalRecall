package actions;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.ActionEvent;
import state.AudioState;

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
        audioState.stop();
        audioState.setAudioProgressWithoutUpdatingActions(0);
    }

    @Override
    public void update() {
        if (audioState.audioOpen()) {
            setEnabled(audioState.isPlaying());
        } else {
            setEnabled(false);
        }
    }
}
