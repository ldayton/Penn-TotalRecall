package actions;

import events.EventDispatchBus;
import events.ExitRequestedEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.ActionEvent;

/**
 * Exits the application using the event-driven system.
 *
 * <p>Publishes ExitRequestedEvent which triggers proper cleanup through the event system before
 * application shutdown.
 */
@Singleton
public class ExitAction extends BaseAction {

    private final EventDispatchBus eventBus;

    @Inject
    public ExitAction(EventDispatchBus eventBus) {
        super("Exit", "Exit the application");
        this.eventBus = eventBus;
        setEnabled(true);
    }

    @Override
    protected void performAction(ActionEvent e) {
        eventBus.publish(new ExitRequestedEvent());
    }
}
