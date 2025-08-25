package actions;

import audio.AudioPlayer;
import events.EventDispatchBus;
import events.ScreenSeekRequestedEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.ActionEvent;
import state.AudioState;

/** Seeks the audio position by one screen width forward or backward. */
@Singleton
public class ScreenSeekAction extends BaseAction {

    private final AudioState audioState;
    private final EventDispatchBus eventBus;
    private final boolean forward;

    @Inject
    public ScreenSeekAction(AudioState audioState, EventDispatchBus eventBus) {
        super("Screen Seek", "Seek by screen width");
        this.audioState = audioState;
        this.eventBus = eventBus;
        this.forward = true; // Default to forward
    }

    @Override
    protected void performAction(ActionEvent e) {
        eventBus.publish(
                new ScreenSeekRequestedEvent(
                        forward
                                ? ScreenSeekRequestedEvent.Direction.FORWARD
                                : ScreenSeekRequestedEvent.Direction.BACKWARD));
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
