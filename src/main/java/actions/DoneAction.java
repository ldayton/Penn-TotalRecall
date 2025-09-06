package actions;

import events.AnnotationCompleteRequestedEvent;
import events.AppStateChangedEvent;
import events.EventDispatchBus;
import events.FocusRequestedEvent;
import events.Subscribe;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.ActionEvent;
import lombok.NonNull;
import s2.AudioSessionStateMachine;

/**
 * Marks the current annotation file complete and then switches program state to reflect that no
 * audio file is open.
 *
 * <p>Publishes AnnotationCompleteRequestedEvent which should be handled by a manager to perform the
 * actual completion logic.
 */
@Singleton
public class DoneAction extends BaseAction {

    private final EventDispatchBus eventBus;
    private AudioSessionStateMachine.State currentState = AudioSessionStateMachine.State.NO_AUDIO;

    @Inject
    public DoneAction(EventDispatchBus eventBus) {
        super("Mark Complete", "Mark current annotation file complete");
        this.eventBus = eventBus;
        eventBus.subscribe(this);
    }

    @Override
    protected void performAction(ActionEvent e) {
        eventBus.publish(new AnnotationCompleteRequestedEvent());
        eventBus.publish(new FocusRequestedEvent(FocusRequestedEvent.Component.MAIN_WINDOW));
    }

    @Subscribe
    public void onStateChanged(@NonNull AppStateChangedEvent event) {
        currentState = event.getNewState();
        updateActionState();
    }

    private void updateActionState() {
        // A file can be marked done only if audio is open and not playing
        switch (currentState) {
            case READY, PAUSED -> setEnabled(true);
            default -> setEnabled(false);
        }
    }

    /** A file can be marked done only if audio is open and not playing. */
    @Override
    public void update() {
        // No-op - now using event-driven updates via @Subscribe to AppStateChangedEvent
    }
}
