package core.actions.impl;

import core.actions.Action;
import jakarta.inject.Inject;

public class Last200PlusMoveForwardAction extends Action {

    @Inject
    public Last200PlusMoveForwardAction() {}

    @Override
    public void execute() {}

    @Override
    public boolean isEnabled() {
        return false;
    }
}
