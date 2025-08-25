package actions;

import audio.AudioPlayer;
import events.EventDispatchBus;
import events.FocusRequestedEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.ActionEvent;
import state.AudioState;

@Singleton
public class ReturnToLastPositionAction extends BaseAction {

    private final AudioState audioState;
    private final EventDispatchBus eventBus;

    @Inject
    public ReturnToLastPositionAction(AudioState audioState, EventDispatchBus eventBus) {
        super("Return to Last Position", "Return to the last playback position");
        this.audioState = audioState;
        this.eventBus = eventBus;
    }

    @Override
    protected void performAction(ActionEvent e) {
        long pos = audioState.popLastPlayPos();
        audioState.setAudioProgressAndUpdateActions(pos);
        audioState.getPlayer().playAt(pos);
        // Fire focus requested event - UI will handle focus updates
        eventBus.publish(new FocusRequestedEvent(FocusRequestedEvent.Component.MAIN_WINDOW));
    }

    @Override
    public void update() {
        if (audioState.audioOpen()) {
            if (audioState.hasLastPlayPos()) {
                if (audioState.getPlayer().getStatus() == AudioPlayer.Status.PLAYING) {
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
