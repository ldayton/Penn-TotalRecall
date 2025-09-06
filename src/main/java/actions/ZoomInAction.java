package actions;

import events.AppStateChangedEvent;
import events.EventDispatchBus;
import events.FocusRequestedEvent;
import events.Subscribe;
import events.ZoomInRequestedEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.ActionEvent;
import lombok.NonNull;
import state.AudioSessionStateMachine;

/** Zooms the waveform display in. */
@Singleton
public class ZoomInAction extends BaseAction {

    private final EventDispatchBus eventBus;
    private AudioSessionStateMachine.State currentState = AudioSessionStateMachine.State.NO_AUDIO;

    @Inject
    public ZoomInAction(EventDispatchBus eventBus) {
        super("Zoom In", "Zoom in the waveform display");
        this.eventBus = eventBus;
        eventBus.subscribe(this);
    }

    @Override
    protected void performAction(ActionEvent e) {
        eventBus.publish(new ZoomInRequestedEvent());
        eventBus.publish(new FocusRequestedEvent(FocusRequestedEvent.Component.MAIN_WINDOW));
    }

    @Subscribe
    public void onStateChanged(@NonNull AppStateChangedEvent event) {
        currentState = event.getNewState();
        updateActionState();
    }

    private void updateActionState() {
        // Zooming is enabled only when audio is open and not playing
        switch (currentState) {
            case READY, PAUSED -> setEnabled(true);
            default -> setEnabled(false);
        }
    }

    /** Zooming is enabled only when audio is open and not playing. */
    @Override
    public void update() {
        // No-op - now using event-driven updates via @Subscribe to AppStateChangedEvent
    }
}
