package behaviors.singleact;

import audio.AudioPlayer;
import components.MyMenu;
import control.CurAudio;
import java.awt.event.ActionEvent;

public class ReplayLastPositionAction extends IdentifiedSingleAction {

    @Override
    public void actionPerformed(ActionEvent e) {
        super.actionPerformed(e);
        AudioPlayer player = CurAudio.getPlayer();
        if (player.getStatus() == AudioPlayer.Status.PLAYING) {
            player.stop();
        }
        new ReturnToLastPositionAction()
                .actionPerformed(
                        new ActionEvent(
                                MyMenu.getInstance(),
                                ActionEvent.ACTION_PERFORMED,
                                null,
                                System.currentTimeMillis(),
                                0));
        new PlayPauseAction(false)
                .actionPerformed(
                        new ActionEvent(MyMenu.getInstance(), ActionEvent.ACTION_PERFORMED, null));
    }

    @Override
    public void update() {
        if (CurAudio.audioOpen()) {
            if (CurAudio.hasLastPlayPos()) {
                setEnabled(true);
            } else {
                setEnabled(false);
            }
        } else {
            setEnabled(false);
        }
    }
}
