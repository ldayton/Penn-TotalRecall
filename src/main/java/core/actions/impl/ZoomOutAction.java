package core.actions.impl;

import core.actions.Action;
import core.dispatch.EventDispatchBus;
import core.dispatch.Subscribe;
import core.events.AppStateChangedEvent;
import core.events.FocusEvent;
import core.events.ZoomOutEvent;
import core.state.AudioSessionStateMachine;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Optional;
import lombok.NonNull;

/** Zooms the waveform display out. */
@Singleton
public class ZoomOutAction extends Action {

    private final EventDispatchBus eventBus;
    private AudioSessionStateMachine.State currentState = AudioSessionStateMachine.State.NO_AUDIO;
    private boolean enabled = false;

    @Inject
    public ZoomOutAction(EventDispatchBus eventBus) {
        this.eventBus = eventBus;
        eventBus.subscribe(this);
    }

    @Override
    public void execute() {
        if (isEnabled()) {
            eventBus.publish(new ZoomOutEvent());
            eventBus.publish(new FocusEvent(FocusEvent.Component.MAIN_WINDOW));
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String getLabel() {
        return "Zoom Out";
    }

    @Override
    public Optional<String> getTooltip() {
        return Optional.of("Zoom out the waveform display");
    }

    @Subscribe
    public void onStateChanged(@NonNull AppStateChangedEvent event) {
        currentState = event.newState();
        updateActionState();
    }

    private void updateActionState() {
        // Zooming is enabled only when audio is open and not playing
        switch (currentState) {
            case READY, PAUSED -> enabled = true;
            default -> enabled = false;
        }
        notifyObservers();
    }
}
