package core.actions.impl;

import core.actions.Action;
import jakarta.inject.Inject;
import java.util.Optional;

public class Last200PlusMoveBackwardAction extends Action {

    @Inject
    public Last200PlusMoveBackwardAction() {}

    @Override
    public void execute() {}

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public String getLabel() {
        return "Backward Small Amount then Replay Last 200 ms";
    }

    @Override
    public Optional<String> getTooltip() {
        return Optional.of("Move backward then replay last 200ms");
    }
}
