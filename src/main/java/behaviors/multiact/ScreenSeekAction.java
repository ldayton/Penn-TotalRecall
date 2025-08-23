package behaviors.multiact;

import audio.AudioPlayer;
import components.waveform.WaveformDisplay;
import control.CurAudio;
import di.GuiceBootstrap;
import info.GUIConstants;
import java.awt.event.ActionEvent;
import util.WindowService;

public class ScreenSeekAction extends IdentifiedMultiAction {

    public enum Dir {
        FORWARD,
        BACKWARD
    }

    private final Dir dir;

    public ScreenSeekAction(Dir dir) {
        super(dir);
        this.dir = dir;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        super.actionPerformed(e);

        int shift =
                (int)
                        (((double) WaveformDisplay.getInstance().getWidth()
                                        / (double) GUIConstants.zoomlessPixelsPerSecond)
                                * 1000);
        shift -= shift / 5;
        if (dir == Dir.BACKWARD) {
            shift *= -1;
        }

        long curFrame = CurAudio.getAudioProgress();
        long frameShift = CurAudio.getMaster().millisToFrames(shift);
        long naivePosition = curFrame + frameShift;
        long frameLength = CurAudio.getMaster().durationInFrames();

        long finalPosition = naivePosition;

        if (naivePosition < 0) {
            finalPosition = 0;
        } else if (naivePosition >= frameLength) {
            finalPosition = frameLength - 1;
        }

        CurAudio.setAudioProgressAndUpdateActions(finalPosition);
        CurAudio.getPlayer().playAt(finalPosition);
        WindowService windowService = GuiceBootstrap.getInjectedInstance(WindowService.class);
        if (windowService != null) {
            windowService.requestFocus();
        }
    }

    @Override
    public void update() {
        if (CurAudio.audioOpen()) {
            if (CurAudio.getPlayer().getStatus() == AudioPlayer.Status.PLAYING) {
                setEnabled(false);
            } else {
                boolean canSkipForward = true;
                if (CurAudio.getAudioProgress() <= 0) {
                    if (canSkipForward && (dir == Dir.FORWARD)) {
                        setEnabled(true);
                    } else {
                        setEnabled(false);
                    }
                } else if (CurAudio.getAudioProgress()
                        == CurAudio.getMaster().durationInFrames() - 1) {
                    if (dir == Dir.FORWARD) {
                        setEnabled(false);
                    } else {
                        setEnabled(true);
                    }
                } else {
                    if (dir == Dir.FORWARD) {
                        setEnabled(canSkipForward);
                    } else {
                        setEnabled(true);
                    }
                }
            }
        } else {
            setEnabled(false);
        }
    }
}
