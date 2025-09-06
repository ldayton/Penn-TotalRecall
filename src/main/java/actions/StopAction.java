package actions;

import events.AppStateChangedEvent;
import events.AudioStopRequestedEvent;
import events.EventDispatchBus;
import events.FocusRequestedEvent;
import events.Subscribe;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.ActionEvent;
import lombok.NonNull;
import state.AudioSessionStateMachine;

/**
 * Stops audio playback and resets position to the beginning using the event-driven system.
 *
 * <p>Publishes AudioStopRequestedEvent which is handled by AudioSessionManager to stop playback and
 * reset position through the new audio engine.
 */
@Singleton
public class StopAction extends BaseAction {

    private final EventDispatchBus eventBus;
    private AudioSessionStateMachine.State currentState = AudioSessionStateMachine.State.NO_AUDIO;

    @Inject
    public StopAction(EventDispatchBus eventBus) {
        super("Stop", "Stop audio playback and reset to beginning");
        this.eventBus = eventBus;
        eventBus.subscribe(this);
    }

    @Override
    protected void performAction(ActionEvent e) {
        eventBus.publish(new AudioStopRequestedEvent());
        eventBus.publish(new FocusRequestedEvent(FocusRequestedEvent.Component.MAIN_WINDOW));
    }

    @Subscribe
    public void onStateChanged(@NonNull AppStateChangedEvent event) {
        currentState = event.getNewState();
        updateActionState();
    }

    private void updateActionState() {
        // Only enabled when playing
        setEnabled(currentState == AudioSessionStateMachine.State.PLAYING);
    }

    @Override
    public void update() {
        // No-op - now using event-driven updates via @Subscribe to AppStateChangedEvent
    }
}
