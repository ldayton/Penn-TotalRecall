package core.actions.impl;

import core.dispatch.EventDispatchBus;
import core.events.SeekByAmountEvent;
import core.preferences.PreferencesManager;
import jakarta.inject.Inject;

public class SeekBackwardSmallAction extends SeekAction {

    @Inject
    public SeekBackwardSmallAction(
            EventDispatchBus eventBus, PreferencesManager preferencesManager) {
        super(eventBus, preferencesManager, SeekByAmountEvent.Direction.BACKWARD, Size.SMALL);
    }
}
