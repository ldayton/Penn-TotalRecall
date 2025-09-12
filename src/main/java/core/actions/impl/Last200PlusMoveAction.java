package core.actions.impl;

import core.actions.Action;
import core.dispatch.EventDispatchBus;
import core.dispatch.Subscribe;
import core.events.AppStateChangedEvent;
import core.events.FocusEvent;
import core.events.PlayLast200MillisThenMoveEvent;
import core.state.AudioSessionStateMachine;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.NonNull;

/** Moves the audio position by a small amount and then replays the last 200ms. */
@Singleton
public class Last200PlusMoveAction extends Action {
    private final EventDispatchBus eventBus;
    private final boolean forward;
    private AudioSessionStateMachine.State currentState = AudioSessionStateMachine.State.NO_AUDIO;

    @Override
    protected String getDefaultLabel() {
        return "Move and Replay Last 200ms";
    }

    @Inject
    public Last200PlusMoveAction(EventDispatchBus eventBus) {
        // Default to forward - subclasses or factory can create variants
        this(eventBus, true);
    }

    public Last200PlusMoveAction(EventDispatchBus eventBus, boolean forward) {
        this.eventBus = eventBus;
        this.forward = forward;
        eventBus.subscribe(this);
    }

    @Override
    public void execute() {
        eventBus.publish(new PlayLast200MillisThenMoveEvent(forward));
        eventBus.publish(new FocusEvent(FocusEvent.Component.MAIN_WINDOW));
    }

    @Override
    public boolean isEnabled() {
        // Enable when audio is loaded and not playing
        return switch (currentState) {
            case READY, PAUSED -> true;
            default -> false;
        };
    }

    @Subscribe
    public void onStateChanged(@NonNull AppStateChangedEvent event) {
        currentState = event.newState();
        notifyObservers();
    }
}
