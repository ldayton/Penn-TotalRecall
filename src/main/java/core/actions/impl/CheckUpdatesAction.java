package core.actions.impl;

import core.actions.Action;
import core.env.UpdateManager;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

/** Checks for program updates. */
@Singleton
public class CheckUpdatesAction extends Action {
    private final Provider<UpdateManager> updateManagerProvider;

    @Inject
    public CheckUpdatesAction(Provider<UpdateManager> updateManagerProvider) {
        this.updateManagerProvider = updateManagerProvider;
    }

    @Override
    public void execute() {
        // Manual check: show dialog even when up-to-date
        updateManagerProvider.get().checkForUpdateManually();
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
