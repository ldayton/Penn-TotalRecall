package core.actions;

import events.AppStateChangedEvent;
import events.AudioStopRequestedEvent;
import events.EventDispatchBus;
import events.FocusRequestedEvent;
import events.Subscribe;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.NonNull;
import state.AudioSessionStateMachine;

/**
 * Stops audio playback and resets position to the beginning using the event-driven system.
 *
 * <p>Publishes AudioStopRequestedEvent which is handled by AudioSessionManager to stop playback and
 * reset position through the new audio engine.
 */
@Singleton
public class StopAction implements Action {

    private final EventDispatchBus eventBus;
    private AudioSessionStateMachine.State currentState = AudioSessionStateMachine.State.NO_AUDIO;
    private boolean enabled = false;

    @Inject
    public StopAction(EventDispatchBus eventBus) {
        this.eventBus = eventBus;
        eventBus.subscribe(this);
    }

    @Override
    public void execute() {
        if (isEnabled()) {
            eventBus.publish(new AudioStopRequestedEvent());
            eventBus.publish(new FocusRequestedEvent(FocusRequestedEvent.Component.MAIN_WINDOW));
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String getLabel() {
        return "Stop";
    }

    @Override
    public String getTooltip() {
        return "Stop audio playback and reset to beginning";
    }

    @Subscribe
    public void onStateChanged(@NonNull AppStateChangedEvent event) {
        currentState = event.getNewState();
        updateActionState();
    }

    private void updateActionState() {
        // Only enabled when playing
        enabled = (currentState == AudioSessionStateMachine.State.PLAYING);
    }
}
