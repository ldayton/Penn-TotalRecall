package core.actions;

import core.dispatch.EventDispatchBus;
import core.events.ExitRequestedEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Exits the application using the event-driven system.
 *
 * <p>Publishes ExitRequestedEvent which triggers proper cleanup through the event system before
 * application shutdown.
 */
@Singleton
public class ExitAction extends Action {

    private final EventDispatchBus eventBus;

    @Inject
    public ExitAction(EventDispatchBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public void execute() {
        eventBus.publish(new ExitRequestedEvent());
    }

    @Override
    public boolean isEnabled() {
        return true; // Always enabled
    }

    @Override
    public String getLabel() {
        return "Exit";
    }

    @Override
    public String getTooltip() {
        return "Exit the application";
    }
}
