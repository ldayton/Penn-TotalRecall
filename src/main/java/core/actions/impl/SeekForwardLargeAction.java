package core.actions.impl;

import core.dispatch.EventDispatchBus;
import core.events.SeekByAmountEvent;
import core.preferences.PreferencesManager;
import jakarta.inject.Inject;

public class SeekForwardLargeAction extends SeekAction {

    @Inject
    public SeekForwardLargeAction(
            EventDispatchBus eventBus, PreferencesManager preferencesManager) {
        super(eventBus, preferencesManager, SeekByAmountEvent.Direction.FORWARD, Size.LARGE);
    }
}
