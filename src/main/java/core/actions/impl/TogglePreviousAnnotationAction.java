package core.actions.impl;

import core.actions.Action;
import jakarta.inject.Inject;

public class TogglePreviousAnnotationAction extends Action {

    @Inject
    public TogglePreviousAnnotationAction() {}

    @Override
    public void execute() {}

    @Override
    public boolean isEnabled() {
        return false;
    }
}
