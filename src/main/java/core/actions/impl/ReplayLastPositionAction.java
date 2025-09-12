package core.actions.impl;

import core.actions.Action;
import jakarta.inject.Inject;

public class ReplayLastPositionAction extends Action {

    @Inject
    public ReplayLastPositionAction() {}

    @Override
    public void execute() {}

    @Override
    public boolean isEnabled() {
        return false;
    }
}
