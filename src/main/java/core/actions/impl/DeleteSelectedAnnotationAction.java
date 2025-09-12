package core.actions.impl;

import core.actions.Action;
import jakarta.inject.Inject;

public class DeleteSelectedAnnotationAction extends Action {

    @Inject
    public DeleteSelectedAnnotationAction() {}

    @Override
    public void execute() {}

    @Override
    public boolean isEnabled() {
        return false;
    }
}
