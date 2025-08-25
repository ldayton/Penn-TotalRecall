package actions;

import audio.AudioPlayer;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.ActionEvent;
import state.AudioState;

@Singleton
public class ReplayLastPositionAction extends BaseAction {

    private final AudioState audioState;
    private final ReturnToLastPositionAction returnToLastPositionAction;
    private final PlayPauseAction playPauseAction;

    @Inject
    public ReplayLastPositionAction(
            AudioState audioState,
            ReturnToLastPositionAction returnToLastPositionAction,
            PlayPauseAction playPauseAction) {
        super("Replay Last Position", "Replay from the last playback position");
        this.audioState = audioState;
        this.returnToLastPositionAction = returnToLastPositionAction;
        this.playPauseAction = playPauseAction;
    }

    @Override
    protected void performAction(ActionEvent e) {
        AudioPlayer player = audioState.getPlayer();
        if (player.getStatus() == AudioPlayer.Status.PLAYING) {
            player.stop();
        }

        // Return to last position and then play
        returnToLastPositionAction.actionPerformed(e);
        playPauseAction.actionPerformed(e);
    }

    @Override
    public void update() {
        if (audioState.audioOpen()) {
            if (audioState.hasLastPlayPos()) {
                setEnabled(true);
            } else {
                setEnabled(false);
            }
        } else {
            setEnabled(false);
        }
    }
}
