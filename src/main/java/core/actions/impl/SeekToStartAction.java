package core.actions.impl;

import core.actions.Action;
import core.audio.AudioSessionStateMachine;
import core.dispatch.EventDispatchBus;
import core.dispatch.Subscribe;
import core.events.AppStateChangedEvent;
import core.events.FocusEvent;
import core.events.SeekToStartEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.NonNull;

/**
 * Stops audio playback and resets position to the beginning of the file.
 *
 * <p>Publishes SeekToStartEvent to stop playback and reset position to the start.
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
            // Stop playback and reset to start
            eventBus.publish(new SeekToStartEvent());
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
