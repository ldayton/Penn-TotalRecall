package core.actions.impl;

import core.actions.Action;
import jakarta.inject.Inject;
import java.util.Optional;

public class AnnotateIntrusionAction extends Action {

    @Inject
    public AnnotateIntrusionAction() {}

    @Override
    public void execute() {}

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public String getLabel() {
        return "Commit Annotation as Intrusion";
    }

    @Override
    public Optional<String> getTooltip() {
        return Optional.of("Commit current annotation as intrusion");
    }
}
