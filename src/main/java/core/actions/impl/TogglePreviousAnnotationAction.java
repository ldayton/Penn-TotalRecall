package core.actions.impl;

import core.actions.Action;
import jakarta.inject.Inject;
import java.util.Optional;

public class TogglePreviousAnnotationAction extends Action {

    @Inject
    public TogglePreviousAnnotationAction() {}

    @Override
    public void execute() {}

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public String getLabel() {
        return "Toggle Previous Annotation";
    }

    @Override
    public Optional<String> getTooltip() {
        return Optional.of("Move to previous annotation");
    }
}
