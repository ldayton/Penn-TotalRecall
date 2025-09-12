package core.actions.impl;

import core.actions.Action;
import jakarta.inject.Inject;

public class Last200PlusMoveBackwardAction extends Action {

    @Inject
    public Last200PlusMoveBackwardAction() {}

    @Override
    public void execute() {}

    @Override
    public boolean isEnabled() {
        return false;
    }
}
