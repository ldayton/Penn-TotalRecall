package core.actions.impl;

import core.actions.Action;
import jakarta.inject.Inject;
import java.util.Optional;

public class DeleteSelectedAnnotationAction extends Action {

    @Inject
    public DeleteSelectedAnnotationAction() {}

    @Override
    public void execute() {}

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public String getLabel() {
        return "Delete Selected Annotation on Waveform";
    }

    @Override
    public Optional<String> getTooltip() {
        return Optional.of("Delete the selected annotation");
    }
}
