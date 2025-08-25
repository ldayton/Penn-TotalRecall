package actions;

import control.PreferencesRequestedEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.ActionEvent;
import util.EventDispatchBus;

/** Launches the preferences window. */
@Singleton
public class PreferencesAction extends BaseAction {

    private final EventDispatchBus eventBus;

    @Inject
    public PreferencesAction(EventDispatchBus eventBus) {
        super("Preferences", "Open preferences window");
        this.eventBus = eventBus;
    }

    /**
     * Performs the Action by setting the PreferencesFrame to visible.
     *
     * <p>If this is the first call, the PreferencesFrame and internal components will actually be
     * created.
     */
    @Override
    protected void performAction(ActionEvent e) {
        // Fire preferences requested event - UI will handle opening the preferences window
        eventBus.publish(new PreferencesRequestedEvent());
    }

    /** PreferencesAction is always enabled. */
    @Override
    public void update() {
        // Always enabled
    }
}
