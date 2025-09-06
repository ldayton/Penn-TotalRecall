package actions;

import events.AppStateChangedEvent;
import events.EventDispatchBus;
import events.ScreenSeekRequestedEvent;
import events.Subscribe;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.ActionEvent;
import lombok.NonNull;
import state.AudioSessionStateMachine;

/** Seeks the audio position backward by one screen width. */
@Singleton
public class ScreenSeekBackwardAction extends BaseAction {

    private final EventDispatchBus eventBus;
    private AudioSessionStateMachine.State currentState = AudioSessionStateMachine.State.NO_AUDIO;

    @Inject
    public ScreenSeekBackwardAction(EventDispatchBus eventBus) {
        super("Screen Backward", "Seek backward by screen width");
        this.eventBus = eventBus;
        eventBus.subscribe(this);
    }

    @Override
    protected void performAction(ActionEvent e) {
        eventBus.publish(new ScreenSeekRequestedEvent(ScreenSeekRequestedEvent.Direction.BACKWARD));
    }

    @Subscribe
    public void onStateChanged(@NonNull AppStateChangedEvent event) {
        currentState = event.getNewState();
        updateActionState();
    }

    private void updateActionState() {
        // Enable when audio is loaded but not playing
        switch (currentState) {
            case READY, PAUSED -> setEnabled(true);
            default -> setEnabled(false);
        }
    }

    @Override
    public void update() {
        // No-op - now using event-driven updates via @Subscribe to AppStateChangedEvent
    }
}
