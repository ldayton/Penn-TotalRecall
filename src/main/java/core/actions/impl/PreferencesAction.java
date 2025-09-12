package core.actions.impl;

import core.actions.Action;
import core.dispatch.EventDispatchBus;
import core.events.PreferencesEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/** Launches the preferences window. */
@Singleton
public class PreferencesAction extends Action {

    private final EventDispatchBus eventBus;

    @Inject
    public PreferencesAction(EventDispatchBus eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * Performs the Action by setting the PreferencesFrame to visible.
     *
     * <p>If this is the first call, the PreferencesFrame and internal components will actually be
     * created.
     */
    @Override
    public void execute() {
        // Fire preferences requested event - UI will handle opening the preferences window
        eventBus.publish(new PreferencesEvent());
    }

    @Override
    public boolean isEnabled() {
        return true; // Always enabled
    }
}
