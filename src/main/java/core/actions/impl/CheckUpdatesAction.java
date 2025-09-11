package core.actions.impl;

import core.actions.Action;
import core.env.UpdateManager;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import java.util.Optional;

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

    @Override
    public String getLabel() {
        return "Check For Updates";
    }

    @Override
    public Optional<String> getTooltip() {
        return Optional.of("Check for program updates");
    }
}
