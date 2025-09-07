package actions;

import core.dispatch.EventDispatchBus;
import core.dispatch.Subscribe;
import core.events.AppStateChangedEvent;
import core.events.SeekScreenEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.ActionEvent;
import lombok.NonNull;
import state.AudioSessionStateMachine;

/** Seeks the audio position forward by one screen width. */
@Singleton
public class ScreenSeekForwardAction extends BaseAction {

    private final EventDispatchBus eventBus;
    private AudioSessionStateMachine.State currentState = AudioSessionStateMachine.State.NO_AUDIO;

    @Inject
    public ScreenSeekForwardAction(EventDispatchBus eventBus) {
        super("Screen Forward", "Seek forward by screen width");
        this.eventBus = eventBus;
        eventBus.subscribe(this);
    }

    @Override
    protected void performAction(ActionEvent e) {
        eventBus.publish(new SeekScreenEvent(SeekScreenEvent.Direction.FORWARD));
    }

    @Subscribe
    public void onStateChanged(@NonNull AppStateChangedEvent event) {
        currentState = event.newState();
        updateActionState();
    }

    private void updateActionState() {
        // Enable when audio is loaded but not playing
        switch (currentState) {
            case READY, PAUSED -> setEnabled(true);
            default -> setEnabled(false);
        }
    }
}
