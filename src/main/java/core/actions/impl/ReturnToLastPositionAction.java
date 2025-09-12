package core.actions.impl;

import core.actions.Action;
import jakarta.inject.Inject;

public class ReturnToLastPositionAction extends Action {

    @Inject
    public ReturnToLastPositionAction() {}

    @Override
    public void execute() {}

    @Override
    public boolean isEnabled() {
        return false;
    }
}
