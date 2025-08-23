package behaviors.multiact;

import audio.AudioPlayer;
import behaviors.singleact.ReplayLast200MillisAction;
import components.MyMenu;
import control.CurAudio;
import di.GuiceBootstrap;
import env.PreferencesManager;
import info.PreferenceKeys;
import java.awt.event.ActionEvent;

/**
 * A combination of {@link behaviors.singleact.ReplayLast200MillisAction} and {@link
 * behaviors.multiact.SeekAction}.
 */
public class Last200PlusMoveAction extends IdentifiedMultiAction {

    public enum Direction {
        BACKWARD,
        FORWARD
    }

    private final ReplayLast200MillisAction replayer;

    private int shift;
    private final Direction dir;

    public Last200PlusMoveAction(Direction dir) {
        super(dir);
        this.dir = dir;
        replayer = new ReplayLast200MillisAction();
        var preferencesManager =
                GuiceBootstrap.getRequiredInjectedInstance(
                        PreferencesManager.class, "PreferencesManager");
        shift =
                preferencesManager.getInt(
                        PreferenceKeys.SMALL_SHIFT, PreferenceKeys.DEFAULT_SMALL_SHIFT);
        if (dir == Direction.BACKWARD) {
            shift *= -1;
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        super.actionPerformed(e);
        long curFrame = CurAudio.getAudioProgress();
        long frameShift = CurAudio.getMaster().millisToFrames(shift);
        long naivePosition = curFrame + frameShift;
        long frameLength = CurAudio.getMaster().durationInFrames();

        long finalPosition = naivePosition;

        if (naivePosition < 0) {
            finalPosition = 0;
        } else if (naivePosition > frameLength) {
            finalPosition = frameLength;
        }

        CurAudio.setAudioProgressWithoutUpdatingActions(
                finalPosition); // not using setAudioProgressAndUpdateActions() because we don't
        // want to slow down start of playback
        CurAudio.getPlayer().playAt(finalPosition);

        replayer.actionPerformed(
                new ActionEvent(
                        MyMenu.getInstance(),
                        ActionEvent.ACTION_PERFORMED,
                        null,
                        System.currentTimeMillis(),
                        0));

        MyMenu.updateActions();
    }

    @Override
    public void update() {
        if (CurAudio.audioOpen()) {
            if (CurAudio.getPlayer().getStatus() == AudioPlayer.Status.PLAYING) {
                setEnabled(false);
            } else {
                if (CurAudio.getAudioProgress() <= 0) {
                    if (dir == Direction.FORWARD) {
                        setEnabled(true);
                    } else {
                        setEnabled(false);
                    }
                } else if (CurAudio.getAudioProgress()
                        == CurAudio.getMaster().durationInFrames() - 1) {
                    if (dir == Direction.FORWARD) {
                        setEnabled(false);
                    } else {
                        setEnabled(true);
                    }
                } else {
                    setEnabled(true);
                }
            }
        } else {
            setEnabled(false);
        }
    }

    public void updateSeekAmount() {
        var preferencesManager =
                GuiceBootstrap.getRequiredInjectedInstance(
                        PreferencesManager.class, "PreferencesManager");
        shift =
                preferencesManager.getInt(
                        PreferenceKeys.SMALL_SHIFT, PreferenceKeys.DEFAULT_SMALL_SHIFT);
        if (dir == Direction.BACKWARD) {
            shift *= -1;
        }
    }
}
