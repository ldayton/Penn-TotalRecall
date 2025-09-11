package core.actions.impl;

import core.dispatch.EventDispatchBus;
import core.events.SeekByAmountEvent;
import core.preferences.PreferencesManager;
import jakarta.inject.Inject;

public class SeekForwardMediumAction extends SeekAction {

    @Inject
    public SeekForwardMediumAction(
            EventDispatchBus eventBus, PreferencesManager preferencesManager) {
        super(eventBus, preferencesManager, SeekByAmountEvent.Direction.FORWARD, Size.MEDIUM);
    }
}
