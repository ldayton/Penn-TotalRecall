package core.actions.impl;

import core.actions.Action;
import jakarta.inject.Inject;

public class AnnotateIntrusionAction extends Action {

    @Inject
    public AnnotateIntrusionAction() {}

    @Override
    public void execute() {}

    @Override
    public boolean isEnabled() {
        return false;
    }
}
