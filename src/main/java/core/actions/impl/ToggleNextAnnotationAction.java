package core.actions.impl;

import core.actions.Action;
import jakarta.inject.Inject;

public class ToggleNextAnnotationAction extends Action {

    @Inject
    public ToggleNextAnnotationAction() {}

    @Override
    public void execute() {}

    @Override
    public boolean isEnabled() {
        return false;
    }
}
