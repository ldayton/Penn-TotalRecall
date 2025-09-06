package actions;

import events.AppStateChangedEvent;
import events.AudioPlayPauseRequestedEvent;
import events.EventDispatchBus;
import events.FocusRequestedEvent;
import events.Subscribe;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.ActionEvent;
import lombok.NonNull;
import s2.AudioSessionStateMachine;

/**
 * Plays or "pauses" audio using the event-driven system.
 *
 * <p>Publishes AudioPlayPauseRequestedEvent which is handled by AudioSessionManager to control
 * playback through the new audio engine.
 */
@Singleton
public class PlayPauseAction extends BaseAction {

    private static final String PLAY_TEXT = "Play";
    private static final String PAUSE_TEXT = "Pause";

    private final EventDispatchBus eventBus;
    private AudioSessionStateMachine.State currentState = AudioSessionStateMachine.State.NO_AUDIO;

    @Inject
    public PlayPauseAction(EventDispatchBus eventBus) {
        super(PLAY_TEXT, "Play or pause audio playback");
        this.eventBus = eventBus;
        eventBus.subscribe(this);
    }

    @Override
    protected void performAction(ActionEvent e) {
        System.out.println("PlayPauseAction.performAction called, current state: " + currentState);
        eventBus.publish(new AudioPlayPauseRequestedEvent());
        eventBus.publish(new FocusRequestedEvent(FocusRequestedEvent.Component.MAIN_WINDOW));
    }

    @Subscribe
    public void onStateChanged(@NonNull AppStateChangedEvent event) {
        currentState = event.getNewState();
        updateActionState();
    }

    private void updateActionState() {
        switch (currentState) {
            case NO_AUDIO, LOADING, ERROR -> {
                putValue(NAME, PLAY_TEXT);
                setEnabled(false);
            }
            case READY, PAUSED -> {
                putValue(NAME, PLAY_TEXT);
                setEnabled(true);
            }
            case PLAYING -> {
                putValue(NAME, PAUSE_TEXT);
                setEnabled(true);
            }
        }
    }

    @Override
    public void update() {
        // No-op - now using event-driven updates via @Subscribe to AppStateChangedEvent
    }
}
