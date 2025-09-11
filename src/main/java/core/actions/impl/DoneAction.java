package core.actions.impl;

import core.actions.Action;
import core.dispatch.EventDispatchBus;
import core.dispatch.Subscribe;
import core.events.AppStateChangedEvent;
import core.events.CompleteAnnotationEvent;
import core.events.FocusEvent;
import core.state.AudioSessionStateMachine;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Optional;
import lombok.NonNull;

/**
 * Marks the current annotation file complete and then switches program state to reflect that no
 * audio file is open.
 *
 * <p>Publishes AnnotationCompleteRequestedEvent which should be handled by a manager to perform the
 * actual completion logic.
 */
@Singleton
public class DoneAction extends Action {

    private final EventDispatchBus eventBus;
    private AudioSessionStateMachine.State currentState = AudioSessionStateMachine.State.NO_AUDIO;
    private boolean enabled = false;

    @Inject
    public DoneAction(EventDispatchBus eventBus) {
        this.eventBus = eventBus;
        eventBus.subscribe(this);
    }

    @Override
    public void execute() {
        if (isEnabled()) {
            eventBus.publish(new CompleteAnnotationEvent());
            eventBus.publish(new FocusEvent(FocusEvent.Component.MAIN_WINDOW));
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String getLabel() {
        return "Mark Complete";
    }

    @Override
    public Optional<String> getTooltip() {
        return Optional.of("Mark current annotation file complete");
    }

    @Subscribe
    public void onStateChanged(@NonNull AppStateChangedEvent event) {
        currentState = event.newState();
        updateActionState();
    }

    private void updateActionState() {
        // A file can be marked done only if audio is open and not playing
        switch (currentState) {
            case READY, PAUSED -> enabled = true;
            default -> enabled = false;
        }
        // Notify observers that state has changed
        notifyObservers();
    }
}
