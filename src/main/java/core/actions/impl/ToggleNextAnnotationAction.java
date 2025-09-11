package core.actions.impl;

import core.actions.Action;
import jakarta.inject.Inject;
import java.util.Optional;

public class ToggleNextAnnotationAction extends Action {

    @Inject
    public ToggleNextAnnotationAction() {}

    @Override
    public void execute() {}

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public String getLabel() {
        return "Toggle Next Annotation";
    }

    @Override
    public Optional<String> getTooltip() {
        return Optional.of("Move to next annotation");
    }
}
