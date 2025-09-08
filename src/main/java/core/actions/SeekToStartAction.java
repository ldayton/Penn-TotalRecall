package core.actions;

import core.dispatch.EventDispatchBus;
import core.dispatch.Subscribe;
import core.events.AppStateChangedEvent;
import core.events.FocusEvent;
import core.events.SeekEvent;
import core.state.AudioSessionStateMachine;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.NonNull;

/**
 * Seeks audio playback to the beginning of the file.
 *
 * <p>Publishes AudioSeekRequestedEvent with frame 0 to reset position to the start.
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
            // Seek to frame 0 (start of file)
            eventBus.publish(new SeekEvent(0));
            eventBus.publish(new FocusEvent(FocusEvent.Component.MAIN_WINDOW));
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String getLabel() {
        return "Seek to Start";
    }

    @Override
    public String getTooltip() {
        return "Seek to the beginning of the audio file";
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
