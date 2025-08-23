package actions;

import audio.AudioPlayer;
import control.AudioState;
import env.PreferencesManager;
import info.PreferenceKeys;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.ActionEvent;

/** Moves the playback position forward by a small amount and then replays the last 200ms. */
@Singleton
public class Last200PlusMoveForwardAction extends BaseAction {

    private final AudioState audioState;
    private final ReplayLast200MillisAction replayer;
    private int shift;

    @Inject
    public Last200PlusMoveForwardAction(AudioState audioState, ReplayLast200MillisAction replayer) {
        super(
                "Forward Small Amount then Replay Last 200 ms",
                "Move forward by a small amount and replay the last 200ms");
        this.audioState = audioState;
        this.replayer = replayer;

        // Initialize shift amount from preferences
        var preferencesManager =
                di.GuiceBootstrap.getRequiredInjectedInstance(
                        PreferencesManager.class, "PreferencesManager");
        shift =
                preferencesManager.getInt(
                        PreferenceKeys.SMALL_SHIFT, PreferenceKeys.DEFAULT_SMALL_SHIFT);
    }

    @Override
    protected void performAction(ActionEvent e) {
        if (!audioState.audioOpen()) {
            return;
        }

        long curFrame = audioState.getAudioProgress();
        long frameShift = audioState.getMaster().millisToFrames(shift);
        long naivePosition = curFrame + frameShift;
        long frameLength = audioState.getMaster().durationInFrames();

        long finalPosition = naivePosition;

        if (naivePosition < 0) {
            finalPosition = 0;
        } else if (naivePosition > frameLength) {
            finalPosition = frameLength;
        }

        // Set position without updating actions to avoid slowing down playback start
        audioState.setAudioProgressWithoutUpdatingActions(finalPosition);
        audioState.getPlayer().playAt(finalPosition);

        // Trigger the replay action
        replayer.actionPerformed(e);
    }

    @Override
    public void update() {
        if (audioState.audioOpen()) {
            if (audioState.getPlayer().getStatus() == AudioPlayer.Status.PLAYING) {
                setEnabled(false);
            } else {
                if (audioState.getAudioProgress() <= 0) {
                    setEnabled(true);
                } else if (audioState.getAudioProgress()
                        == audioState.getMaster().durationInFrames() - 1) {
                    setEnabled(false);
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
                di.GuiceBootstrap.getRequiredInjectedInstance(
                        PreferencesManager.class, "PreferencesManager");
        shift =
                preferencesManager.getInt(
                        PreferenceKeys.SMALL_SHIFT, PreferenceKeys.DEFAULT_SMALL_SHIFT);
    }
}
