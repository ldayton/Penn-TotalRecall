package actions;

import audio.AudioPlayer;
import components.waveform.WaveformDisplay;
import control.AudioState;
import control.FocusRequestedEvent;
import info.GUIConstants;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.ActionEvent;
import util.EventBus;

/** Seeks the audio position by one screen width forward or backward. */
@Singleton
public class ScreenSeekAction extends BaseAction {

    private final AudioState audioState;
    private final EventBus eventBus;
    private final boolean forward;

    @Inject
    public ScreenSeekAction(AudioState audioState, EventBus eventBus) {
        super("Screen Seek", "Seek by screen width");
        this.audioState = audioState;
        this.eventBus = eventBus;
        this.forward = true; // Default to forward
    }

    @Override
    protected void performAction(ActionEvent e) {
        int shift =
                (int)
                        (((double) WaveformDisplay.getInstance().getWidth()
                                        / (double) GUIConstants.zoomlessPixelsPerSecond)
                                * 1000);
        shift -= shift / 5;
        if (!forward) {
            shift *= -1;
        }

        long curFrame = audioState.getAudioProgress();
        long frameShift = audioState.getMaster().millisToFrames(shift);
        long naivePosition = curFrame + frameShift;
        long frameLength = audioState.getMaster().durationInFrames();

        long finalPosition = naivePosition;

        if (naivePosition < 0) {
            finalPosition = 0;
        } else if (naivePosition >= frameLength) {
            finalPosition = frameLength - 1;
        }

        audioState.setAudioProgressAndUpdateActions(finalPosition);
        audioState.getPlayer().playAt(finalPosition);
        eventBus.publish(new FocusRequestedEvent());
    }

    @Override
    public void update() {
        if (audioState.audioOpen()) {
            if (audioState.getPlayer().getStatus() == AudioPlayer.Status.PLAYING) {
                setEnabled(false);
            } else {
                setEnabled(true);
            }
        } else {
            setEnabled(false);
        }
    }
}
