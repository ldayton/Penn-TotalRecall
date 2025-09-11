package core.actions.impl;

import core.actions.Action;
import jakarta.inject.Inject;
import java.util.Optional;

public class ReplayLastPositionAction extends Action {

    @Inject
    public ReplayLastPositionAction() {}

    @Override
    public void execute() {}

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public String getLabel() {
        return "Replay";
    }

    @Override
    public Optional<String> getTooltip() {
        return Optional.of("Replay from last position");
    }
}
