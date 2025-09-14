package core.actions.impl;

import core.actions.Action;
import core.audio.session.AudioSessionStateMachine;
import core.dispatch.EventDispatchBus;
import core.dispatch.Subscribe;
import core.events.AppStateChangedEvent;
import core.events.FocusEvent;
import core.events.PlayPauseEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.NonNull;

/**
 * Plays or "pauses" audio using the event-driven system.
 *
 * <p>Publishes AudioPlayPauseRequestedEvent which is handled by AudioSessionManager to control
 * playback through the new audio engine.
 */
@Singleton
public class PlayPauseAction extends Action {

    private final EventDispatchBus eventBus;
    private AudioSessionStateMachine.State currentState = AudioSessionStateMachine.State.NO_AUDIO;
    private boolean enabled = false;
    private boolean isPlaying = false;

    @Inject
    public PlayPauseAction(EventDispatchBus eventBus) {
        this.eventBus = eventBus;
        eventBus.subscribe(this);
    }

    @Override
    public void execute() {
        if (isEnabled()) {
            eventBus.publish(new PlayPauseEvent());
            eventBus.publish(new FocusEvent(FocusEvent.Component.MAIN_WINDOW));
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String getLabel() {
        // Dynamic label based on playback state
        return isPlaying ? "Pause" : "Play";
    }

    @Subscribe
    public void onStateChanged(@NonNull AppStateChangedEvent event) {
        currentState = event.newState();
        updateActionState();
    }

    private void updateActionState() {
        switch (currentState) {
            case NO_AUDIO, LOADING, ERROR -> {
                isPlaying = false;
                enabled = false;
            }
            case READY, PAUSED -> {
                isPlaying = false;
                enabled = true;
            }
            case PLAYING -> {
                isPlaying = true;
                enabled = true;
            }
        }
        // Notify observers that state has changed
        notifyObservers();
    }
}
