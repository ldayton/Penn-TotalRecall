package behaviors.singleact;

import audio.AudioPlayer;
import control.CurAudio;
import di.GuiceBootstrap;
import java.awt.event.ActionEvent;
import util.WindowService;

/**
 * Plays or "pauses" audio.
 *
 * <p>Remember that a "pause" is a normal stop as far as the <code>PrecisionPlayer</code> is
 * concerned. The program however remembers the stop position for future resumption.
 */
public class PlayPauseAction extends IdentifiedSingleAction {

    private static final String playText = "Play";
    private static final String pauseText = "Pause";

    private final boolean isDummy;

    /**
     * Dummy actions don't actually perform the action, they are used only for the benefit of visual
     * representation of the action for a JMenuItem.
     *
     * <p>This workaround is necessary to prevent visual jumps in the waveform at every pause. This
     * may be related to a bug report I am submitting to Oracle regarding events moving through the
     * queue very slowly when associated with a menu item.
     *
     * @param dummy <code>false</code> iff this object will actually perform its action
     */
    public PlayPauseAction(boolean dummy) {
        isDummy = dummy;
    }

    /**
     * Performs action by starting/stopping <code>PrecisionPlayer</code> and storing the frame in
     * <code>CurAudio</code> class as appropriate.
     *
     * @param e The <code>ActionEvent</code> provided by the trigger
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        super.actionPerformed(e);
        if (isDummy) {
            return;
        }
        AudioPlayer player = CurAudio.getPlayer();
        if (player.getStatus() == AudioPlayer.Status.PLAYING) { // PAUSE
            long frame = player.stop();
            CurAudio.setAudioProgressWithoutUpdatingActions(frame);
            long numFrames = CurAudio.getMaster().millisToFrames(200);
            player.playShortInterval(frame - numFrames, frame - 1);
            player.playAt(frame);
        } else { // PLAY/RESUME
            long pos = CurAudio.getAudioProgress();
            player.playAt(pos);
            CurAudio.pushPlayPos(pos);
        }
        WindowService windowService = GuiceBootstrap.getInjectedInstance(WindowService.class);
        if (windowService != null) {
            windowService.requestFocus();
        }
    }

    /**
     * Play/pause is enabled when audio is open, not playing, and not on the final frame.
     *
     * <p>The "pause" label is used when audio is playing. The "play" label is used otherwise,
     * whether or not the action is enabled.
     */
    @Override
    public void update() {
        if (CurAudio.audioOpen()) {
            if (CurAudio.getAudioProgress() == CurAudio.getMaster().durationInFrames() - 1) {
                setEnabled(false);
            } else {
                setEnabled(true);
            }
            if (CurAudio.getPlayer().getStatus() == AudioPlayer.Status.PLAYING) {
                putValue(NAME, pauseText);
            } else {
                putValue(NAME, playText);
            }
        } else {
            putValue(NAME, playText);
            setEnabled(false);
        }
    }
}
