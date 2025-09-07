package actions;

import env.UpdateManager;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import java.awt.event.ActionEvent;

/** Checks for program updates. */
@Singleton
public class CheckUpdatesAction extends BaseAction {
    private final Provider<UpdateManager> updateManagerProvider;

    @Inject
    public CheckUpdatesAction(Provider<UpdateManager> updateManagerProvider) {
        super("Check For Updates", "Check for program updates");
        this.updateManagerProvider = updateManagerProvider;
        setEnabled(true);
    }

    @Override
    protected void performAction(ActionEvent e) {
        // Manual check: show dialog even when up-to-date
        updateManagerProvider.get().checkForUpdateManually();
    }
}
