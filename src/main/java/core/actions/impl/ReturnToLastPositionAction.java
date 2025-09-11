package core.actions.impl;

import core.actions.Action;
import jakarta.inject.Inject;
import java.util.Optional;

public class ReturnToLastPositionAction extends Action {

    @Inject
    public ReturnToLastPositionAction() {}

    @Override
    public void execute() {}

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public String getLabel() {
        return "Undo Play";
    }

    @Override
    public Optional<String> getTooltip() {
        return Optional.of("Return to the position prior to hitting play");
    }
}
