package core.actions.impl;

import core.actions.Action;
import core.dispatch.EventDispatchBus;
import core.events.EditShortcutsRequestedEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/** Action to open the keyboard shortcuts editor. */
@Singleton
public class EditShortcutsAction extends Action {

    private final EventDispatchBus eventBus;

    @Inject
    public EditShortcutsAction(EventDispatchBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public void execute() {
        eventBus.publish(new EditShortcutsRequestedEvent());
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
