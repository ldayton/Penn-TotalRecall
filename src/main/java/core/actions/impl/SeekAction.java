package core.actions.impl;

import core.actions.Action;
import core.dispatch.EventDispatchBus;
import core.dispatch.Subscribe;
import core.env.PreferenceKeys;
import core.events.AppStateChangedEvent;
import core.events.FocusEvent;
import core.events.SeekByAmountEvent;
import core.preferences.PreferencesManager;
import core.state.AudioSessionStateMachine;
import lombok.NonNull;

/**
 * Seeks the audio position forward or backward by a pre-defined amount. The actual frame
 * calculations are handled by the audio session manager.
 */
public abstract class SeekAction extends Action {

    public enum Size {
        SMALL,
        MEDIUM,
        LARGE
    }

    private final EventDispatchBus eventBus;
    private final PreferencesManager preferencesManager;
    private final SeekByAmountEvent.Direction direction;
    private final Size size;
    private AudioSessionStateMachine.State currentState = AudioSessionStateMachine.State.NO_AUDIO;

    @Override
    protected String getDefaultLabel() {
        return "Seek";
    }

    protected SeekAction(
            EventDispatchBus eventBus,
            PreferencesManager preferencesManager,
            SeekByAmountEvent.Direction direction,
            Size size) {
        this.eventBus = eventBus;
        this.preferencesManager = preferencesManager;
        this.direction = direction;
        this.size = size;
        eventBus.subscribe(this);
    }

    @Override
    public void execute() {
        int shiftMillis = getShiftAmount();
        eventBus.publish(new SeekByAmountEvent(direction, shiftMillis));
        eventBus.publish(new FocusEvent(FocusEvent.Component.MAIN_WINDOW));
    }

    @Override
    public boolean isEnabled() {
        // Enable when audio is loaded but not playing
        return switch (currentState) {
            case READY, PAUSED -> true;
            default -> false;
        };
    }

    private int getShiftAmount() {
        return switch (size) {
            case LARGE ->
                    preferencesManager.getInt(
                            PreferenceKeys.LARGE_SHIFT, PreferenceKeys.DEFAULT_LARGE_SHIFT);
            case MEDIUM ->
                    preferencesManager.getInt(
                            PreferenceKeys.MEDIUM_SHIFT, PreferenceKeys.DEFAULT_MEDIUM_SHIFT);
            case SMALL ->
                    preferencesManager.getInt(
                            PreferenceKeys.SMALL_SHIFT, PreferenceKeys.DEFAULT_SMALL_SHIFT);
        };
    }

    @Subscribe
    public void onStateChanged(@NonNull AppStateChangedEvent event) {
        currentState = event.newState();
        notifyObservers();
    }
}
