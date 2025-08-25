package actions;

import audio.AudioPlayer;
import components.waveform.SelectionOverlay;
import control.AudioState;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.ActionEvent;

/**
 * Replays the last 200 milliseconds so the annotator can judge whether a word onset has been
 * crossed.
 */
@Singleton
public class ReplayLast200MillisAction extends BaseAction {

    public static final int duration = 200;

    private final AudioState audioState;
    private final SelectionOverlay glassPane;

    @Inject
    public ReplayLast200MillisAction(AudioState audioState, SelectionOverlay glassPane) {
        super("Replay Last 200ms", "Replay the last 200 milliseconds of audio");
        this.audioState = audioState;
        this.glassPane = glassPane;
    }

    /**
     * Performs the action by calling the corresponding PrecisionPlayer function.
     *
     * <p>As per PrecisionPlayer's docs, this replay cannot be stopped once started.
     *
     * @param e The ActionEvent provided by the trigger
     */
    @Override
    protected void performAction(ActionEvent e) {
        AudioPlayer player = audioState.getPlayer();

        long curFrame = audioState.getAudioProgress();
        long numFrames = audioState.getMaster().millisToFrames(duration);

        player.playShortInterval(curFrame - numFrames, curFrame - 1);
        glassPane.flashRectangle();
    }

    /**
     * User can replay last 200 millis when audio is open, not playing, and not on the first frame.
     */
    @Override
    public void update() {
        if (audioState.audioOpen()) {
            if (audioState.getPlayer().getStatus() == AudioPlayer.Status.PLAYING) {
                setEnabled(false);
            } else {
                if (audioState.getAudioProgress() <= 0) {
                    setEnabled(false);
                } else {
                    setEnabled(true);
                }
            }
        } else {
            setEnabled(false);
        }
    }
}
