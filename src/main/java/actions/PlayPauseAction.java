package actions;

import audio.AudioPlayer;
import events.EventDispatchBus;
import events.FocusRequestedEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.ActionEvent;
import state.AudioState;

/**
 * Plays or "pauses" audio.
 *
 * <p>Remember that a "pause" is a normal stop as far as the <code>PrecisionPlayer</code> is
 * concerned. The program however remembers the stop position for future resumption.
 */
@Singleton
public class PlayPauseAction extends BaseAction {

    private static final String PLAY_TEXT = "Play";
    private static final String PAUSE_TEXT = "Pause";

    private final AudioState audioState;
    private final EventDispatchBus eventBus;

    @Inject
    public PlayPauseAction(AudioState audioState, EventDispatchBus eventBus) {
        super(PLAY_TEXT, "Play or pause audio playback");
        this.audioState = audioState;
        this.eventBus = eventBus;
    }

    @Override
    protected void performAction(ActionEvent e) {
        if (!audioState.audioOpen()) {
            return;
        }

        AudioPlayer player = audioState.getPlayer();
        if (player.getStatus() == AudioPlayer.Status.PLAYING) { // PAUSE
            long frame = player.stop();
            audioState.setAudioProgressWithoutUpdatingActions(frame);
            long numFrames = audioState.getCalculator().millisToFrames(200);

            // Clamp preview start to 0 to allow preview near file start; ensure valid range
            if (frame >= 2) { // need at least 2 frames to form a valid [start, end)
                long previewStart = Math.max(0, frame - numFrames);
                long previewEnd = frame - 1; // exclusive end in player validation
                if (previewEnd > previewStart) {
                    player.playShortInterval(previewStart, previewEnd);
                }
            }
            // Note: Removed playAt(frame) - pause should actually pause, not resume immediately
        } else { // PLAY/RESUME
            long pos = audioState.getAudioProgress();
            player.playAt(pos);
            audioState.pushPlayPos(pos);
        }

        // Fire focus requested event - UI will handle focus updates
        eventBus.publish(new FocusRequestedEvent(FocusRequestedEvent.Component.MAIN_WINDOW));
    }

    @Override
    public void update() {
        if (audioState.audioOpen()) {
            if (audioState.getAudioProgress()
                    == audioState.getCalculator().durationInFrames() - 1) {
                setEnabled(false);
            } else {
                setEnabled(true);
            }

            if (audioState.getPlayer().getStatus() == AudioPlayer.Status.PLAYING) {
                putValue(NAME, PAUSE_TEXT);
            } else {
                putValue(NAME, PLAY_TEXT);
            }
        } else {
            putValue(NAME, PLAY_TEXT);
            setEnabled(false);
        }
    }
}
