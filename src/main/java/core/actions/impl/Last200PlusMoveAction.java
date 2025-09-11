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
import java.util.Optional;
import lombok.NonNull;

/** Moves the audio position by a small amount and then replays the last 200ms. */
@Singleton
public class Last200PlusMoveAction extends Action {
    private final EventDispatchBus eventBus;
    private final boolean forward;
    private AudioSessionStateMachine.State currentState = AudioSessionStateMachine.State.NO_AUDIO;

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

    @Override
    public String getLabel() {
        return "Last200PlusMove";
    }

    @Override
    public Optional<String> getTooltip() {
        return Optional.of("Move and replay last 200ms");
    }

    @Subscribe
    public void onStateChanged(@NonNull AppStateChangedEvent event) {
        currentState = event.newState();
        notifyObservers();
    }
}
