package ui.adapters;

import core.actions.Action;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.AbstractAction;
import javax.swing.KeyStroke;

/**
 * Adapter that wraps a core Action for use with Swing.
 *
 * <p>This adapter allows UI-agnostic Action implementations to be used with Swing components while
 * keeping the core action logic free of UI framework dependencies. It automatically observes state
 * changes and updates the Swing representation.
 */
public class SwingAction extends AbstractAction {
    private final Action action;

    public SwingAction(Action action) {
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

        // ShortcutSpec will be handled by UI layer that knows about KeyStrokes
        // For now, skip shortcut handling as it needs platform-specific conversion

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

    private KeyStroke parseShortcut(String shortcut) {
        String processed =
                shortcut.toLowerCase()
                        .replace("cmd", "meta")
                        .replace("command", "meta")
                        .replace("ctrl", "control");

        // Try direct parsing first
        KeyStroke stroke = KeyStroke.getKeyStroke(processed);
        if (stroke != null) {
            return stroke;
        }

        // Try with more complex parsing if needed
        int modifiers = 0;
        String key = shortcut;

        if (processed.contains("+")) {
            String[] parts = processed.split("\\+");
            key = parts[parts.length - 1].toUpperCase();

            for (int i = 0; i < parts.length - 1; i++) {
                String mod = parts[i].trim();
                if (mod.equals("control") || mod.equals("ctrl")) {
                    modifiers |= InputEvent.CTRL_DOWN_MASK;
                } else if (mod.equals("shift")) {
                    modifiers |= InputEvent.SHIFT_DOWN_MASK;
                } else if (mod.equals("alt")) {
                    modifiers |= InputEvent.ALT_DOWN_MASK;
                } else if (mod.equals("meta") || mod.equals("cmd") || mod.equals("command")) {
                    modifiers |= InputEvent.META_DOWN_MASK;
                }
            }
        }

        // Get the key code
        try {
            int keyCode = KeyEvent.class.getField("VK_" + key).getInt(null);
            return KeyStroke.getKeyStroke(keyCode, modifiers);
        } catch (Exception e) {
            return null;
        }
    }
}
