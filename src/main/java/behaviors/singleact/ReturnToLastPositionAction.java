package behaviors.singleact;

import audio.AudioPlayer;
import components.MyFrame;
import control.CurAudio;
import java.awt.event.ActionEvent;

public class ReturnToLastPositionAction extends IdentifiedSingleAction {

    @Override
    public void actionPerformed(ActionEvent e) {
        super.actionPerformed(e);
        long pos = CurAudio.popLastPlayPos();
        CurAudio.setAudioProgressAndUpdateActions(pos);
        CurAudio.getPlayer().playAt(pos);
        MyFrame.getInstance().requestFocusInWindow();
    }

    @Override
    public void update() {
        if (CurAudio.audioOpen()) {
            if (CurAudio.hasLastPlayPos()) {
                if (CurAudio.getPlayer().getStatus() == AudioPlayer.Status.PLAYING) {
                    setEnabled(false);
                } else {
                    setEnabled(true);
                }
            } else {
                setEnabled(false);
            }
        } else {
            setEnabled(false);
        }
    }
}
