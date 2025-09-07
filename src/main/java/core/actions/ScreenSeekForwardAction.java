package core.actions;

import core.dispatch.EventDispatchBus;
import core.dispatch.Subscribe;
import core.events.AppStateChangedEvent;
import core.events.FocusEvent;
import core.events.SeekScreenEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.NonNull;
import state.AudioSessionStateMachine;

/** Seeks the audio position forward by one screen width. */
@Singleton
public class ScreenSeekForwardAction extends Action {

    private final EventDispatchBus eventBus;
    private AudioSessionStateMachine.State currentState = AudioSessionStateMachine.State.NO_AUDIO;
    private boolean enabled = false;

    @Inject
    public ScreenSeekForwardAction(EventDispatchBus eventBus) {
        this.eventBus = eventBus;
        eventBus.subscribe(this);
    }

    @Override
    public void execute() {
        if (isEnabled()) {
            eventBus.publish(new SeekScreenEvent(SeekScreenEvent.Direction.FORWARD));
            eventBus.publish(new FocusEvent(FocusEvent.Component.MAIN_WINDOW));
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String getLabel() {
        return "Screen Forward";
    }

    @Override
    public String getTooltip() {
        return "Seek forward by screen width";
    }

    @Subscribe
    public void onStateChanged(@NonNull AppStateChangedEvent event) {
        currentState = event.newState();
        updateActionState();
    }

    private void updateActionState() {
        boolean oldEnabled = enabled;
        // Enable when audio is loaded but not playing
        enabled =
                switch (currentState) {
                    case READY, PAUSED -> true;
                    default -> false;
                };
        if (oldEnabled != enabled) {
            notifyObservers();
        }
    }
}
