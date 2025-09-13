package core.actions.impl;

import core.actions.Action;
import core.audio.AudioSessionStateMachine;
import core.dispatch.EventDispatchBus;
import core.dispatch.Subscribe;
import core.events.AppStateChangedEvent;
import core.events.FocusEvent;
import core.events.SeekEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.NonNull;

/**
 * Seeks audio playback position to the beginning of the file.
 *
 * <p>Publishes SeekEvent(0) to reset position to the start.
 */
@Singleton
public class SeekToStartAction extends Action {

    private final EventDispatchBus eventBus;
    private AudioSessionStateMachine.State currentState = AudioSessionStateMachine.State.NO_AUDIO;
    private boolean enabled = false;

    @Inject
    public SeekToStartAction(EventDispatchBus eventBus) {
        this.eventBus = eventBus;
        eventBus.subscribe(this);
    }

    @Override
    public void execute() {
        if (isEnabled()) {
            // Seek to the beginning of the file
            eventBus.publish(new SeekEvent(0));
            eventBus.publish(new FocusEvent(FocusEvent.Component.MAIN_WINDOW));
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Subscribe
    public void onStateChanged(@NonNull AppStateChangedEvent event) {
        currentState = event.newState();
        updateActionState();
    }

    private void updateActionState() {
        // Enabled when audio is loaded (any state except NO_AUDIO, LOADING, ERROR)
        switch (currentState) {
            case READY, PAUSED, PLAYING -> enabled = true;
            default -> enabled = false;
        }
        // Notify observers that state has changed
        notifyObservers();
    }
}
