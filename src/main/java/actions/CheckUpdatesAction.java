package actions;

import control.InfoRequestedEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.ActionEvent;
import util.EventBus;

/** Checks for program updates. */
@Singleton
public class CheckUpdatesAction extends BaseAction {
    private final EventBus eventBus;

    @Inject
    public CheckUpdatesAction(EventBus eventBus) {
        super("Check For Updates", "Check for program updates");
        this.eventBus = eventBus;
    }

    @Override
    protected void performAction(ActionEvent e) {
        // TODO: Implement actual update checking logic
        eventBus.publish(new InfoRequestedEvent("Update checking not yet implemented."));
    }

    @Override
    public void update() {
        setEnabled(true);
    }
}
