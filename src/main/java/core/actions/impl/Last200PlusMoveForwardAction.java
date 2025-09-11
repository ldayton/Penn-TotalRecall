package core.actions.impl;

import core.actions.Action;
import jakarta.inject.Inject;
import java.util.Optional;

public class Last200PlusMoveForwardAction extends Action {

    @Inject
    public Last200PlusMoveForwardAction() {}

    @Override
    public void execute() {}

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public String getLabel() {
        return "Forward Small Amount then Replay Last 200 ms";
    }

    @Override
    public Optional<String> getTooltip() {
        return Optional.of("Move forward then replay last 200ms");
    }
}
