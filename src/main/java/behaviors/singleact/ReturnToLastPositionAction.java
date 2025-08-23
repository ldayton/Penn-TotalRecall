package behaviors.singleact;

import audio.AudioPlayer;
import control.CurAudio;
import di.GuiceBootstrap;
import java.awt.event.ActionEvent;
import util.WindowService;

public class ReturnToLastPositionAction extends IdentifiedSingleAction {

    @Override
    public void actionPerformed(ActionEvent e) {
        super.actionPerformed(e);
        long pos = CurAudio.popLastPlayPos();
        CurAudio.setAudioProgressAndUpdateActions(pos);
        CurAudio.getPlayer().playAt(pos);
        WindowService windowService = GuiceBootstrap.getInjectedInstance(WindowService.class);
        if (windowService == null) {
            throw new IllegalStateException("WindowService not available via DI");
        }
        windowService.requestFocus();
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
