package ui.adapters;

import core.actions.Action;
import core.actions.ActionConfig;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.KeyStroke;
import lombok.NonNull;

/**
 * Adapter that wraps a core Action for use with Swing.
 *
 * <p>This adapter allows UI-agnostic Action implementations to be used with Swing components while
 * keeping the core action logic free of UI framework dependencies. It automatically observes state
 * changes and updates the Swing representation.
 */
public class SwingAction extends AbstractAction {
    private final Action action;

    public SwingAction(
            @NonNull Action action, ActionConfig config, ShortcutConverter shortcutConverter) {
        super(action.getLabel());
        this.action = action;

        // Set tooltip if provided
        action.getTooltip()
                .ifPresent(
                        tooltip -> {
                            if (!tooltip.isEmpty()) {
                                putValue(SHORT_DESCRIPTION, tooltip);
                            }
                        });

        // Observe state changes
        action.addObserver(this::updateFromAction);

        // Set keyboard shortcut from config if provided
        if (config != null && config.shortcut().isPresent() && shortcutConverter != null) {
            KeyStroke keyStroke = shortcutConverter.toKeyStroke(config.shortcut().get());
            if (keyStroke != null) {
                putValue(ACCELERATOR_KEY, keyStroke);
            }
        }

        // Set initial enabled state
        setEnabled(action.isEnabled());
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (action.isEnabled()) {
            action.execute();
        }
    }

    /** Update this Swing action from the wrapped action's current state. */
    private void updateFromAction() {
        setEnabled(action.isEnabled());
        putValue(NAME, action.getLabel());
        action.getTooltip()
                .ifPresent(
                        tooltip -> {
                            if (!tooltip.isEmpty()) {
                                putValue(SHORT_DESCRIPTION, tooltip);
                            }
                        });
    }

    public Action getCoreAction() {
        return action;
    }
}
