package actions;

import core.dispatch.EventDispatchBus;
import core.dispatch.Subscribe;
import core.events.AppStateChangedEvent;
import core.events.FocusEvent;
import core.events.PlayLast200MillisThenMoveEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.ActionEvent;
import javax.swing.Action;
import lombok.NonNull;
import state.AudioSessionStateMachine;

/** Moves the audio position by a small amount and then replays the last 200ms. */
@Singleton
public class Last200PlusMoveAction extends BaseAction {
    private final EventDispatchBus eventBus;
    private AudioSessionStateMachine.State currentState = AudioSessionStateMachine.State.NO_AUDIO;

    @Inject
    public Last200PlusMoveAction(EventDispatchBus eventBus) {
        super("Last200PlusMove", "Move and replay last 200ms");
        this.eventBus = eventBus;
        eventBus.subscribe(this);
    }

    @Override
    protected void performAction(ActionEvent e) {
        // Get direction from action name
        String actionName = (String) getValue(Action.NAME);
        boolean forward = actionName.contains("Forward");

        eventBus.publish(new PlayLast200MillisThenMoveEvent(forward));
        eventBus.publish(new FocusEvent(FocusEvent.Component.MAIN_WINDOW));
    }

    @Subscribe
    public void onStateChanged(@NonNull AppStateChangedEvent event) {
        currentState = event.newState();
        updateActionState();
    }

    private void updateActionState() {
        // Enable when audio is loaded and not playing
        switch (currentState) {
            case READY, PAUSED -> setEnabled(true);
            default -> setEnabled(false);
        }
    }
}
