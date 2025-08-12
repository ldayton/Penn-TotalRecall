package behaviors.singleact;

import audio.PrecisionPlayer;
import components.waveform.MyGlassPane;
import control.CurAudio;
import java.awt.event.ActionEvent;

/**
 * Replays the last 200 milliseconds so the annotator can judge whether a word onset has been
 * crossed.
 */
public class ReplayLast200MillisAction extends IdentifiedSingleAction {

    public static final int duration = 200;

    /**
     * Performs the action by calling the corresponding <code>PrecisionPlayer</code> function.
     *
     * <p>As per <code>PrecisionPlayer</code>'s docs, this replay cannot be stopped once started.
     *
     * @param e The <code>ActionEvent</code> provided by the trigger
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        super.actionPerformed(e);
        PrecisionPlayer player = CurAudio.getPlayer();
        player.setLoudness(CurAudio.getDesiredLoudness());

        long curFrame = CurAudio.getAudioProgress();
        long numFrames = CurAudio.getMaster().millisToFrames(duration);

        player.playShortInterval(curFrame - numFrames, curFrame - 1);
        MyGlassPane.getInstance().flashRectangle();
    }

    /**
     * User can replay last 200 millis when audio is open, not playing, and not on the first frame.
     */
    @Override
    public void update() {
        if (CurAudio.audioOpen()) {
            setEnabled(true);
            if (CurAudio.getPlayer().getStatus() == PrecisionPlayer.Status.PLAYING) {
                setEnabled(false);
            } else {
                if (CurAudio.getAudioProgress() <= 0) {
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
