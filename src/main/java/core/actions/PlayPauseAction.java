package core.actions;

import events.AppStateChangedEvent;
import events.AudioPlayPauseRequestedEvent;
import events.EventDispatchBus;
import events.FocusRequestedEvent;
import events.Subscribe;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.NonNull;
import state.AudioSessionStateMachine;

/**
 * Plays or "pauses" audio using the event-driven system.
 *
 * <p>Publishes AudioPlayPauseRequestedEvent which is handled by AudioSessionManager to control
 * playback through the new audio engine.
 */
@Singleton
public class PlayPauseAction extends Action {

    private static final String PLAY_TEXT = "Play";
    private static final String PAUSE_TEXT = "Pause";

    private final EventDispatchBus eventBus;
    private AudioSessionStateMachine.State currentState = AudioSessionStateMachine.State.NO_AUDIO;
    private boolean enabled = false;
    private String label = PLAY_TEXT;

    @Inject
    public PlayPauseAction(EventDispatchBus eventBus) {
        this.eventBus = eventBus;
        eventBus.subscribe(this);
    }

    @Override
    public void execute() {
        if (isEnabled()) {
            eventBus.publish(new AudioPlayPauseRequestedEvent());
            eventBus.publish(new FocusRequestedEvent(FocusRequestedEvent.Component.MAIN_WINDOW));
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public String getTooltip() {
        return "Play or pause audio playback";
    }

    @Subscribe
    public void onStateChanged(@NonNull AppStateChangedEvent event) {
        currentState = event.getNewState();
        updateActionState();
    }

    private void updateActionState() {
        switch (currentState) {
            case NO_AUDIO, LOADING, ERROR -> {
                label = PLAY_TEXT;
                enabled = false;
            }
            case READY, PAUSED -> {
                label = PLAY_TEXT;
                enabled = true;
            }
            case PLAYING -> {
                label = PAUSE_TEXT;
                enabled = true;
            }
        }
        // Notify observers that state has changed
        notifyObservers();
    }
}
