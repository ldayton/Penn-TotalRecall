package ui.swing;

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

    /**
     * Create a SwingAction that wraps the given core Action.
     *
     * @param action the core action to wrap
     */
    public SwingAction(Action action) {
        super(action.getLabel());
        this.action = action;

        // Set tooltip if provided
        String tooltip = action.getTooltip();
        if (tooltip != null && !tooltip.isEmpty()) {
            putValue(SHORT_DESCRIPTION, tooltip);
        }

        // Observe state changes
        action.addObserver(this::updateFromAction);

        // Parse and set keyboard shortcut if provided
        String shortcut = action.getShortcut();
        if (shortcut != null && !shortcut.isEmpty()) {
            try {
                KeyStroke keyStroke = parseShortcut(shortcut);
                if (keyStroke != null) {
                    putValue(ACCELERATOR_KEY, keyStroke);
                }
            } catch (Exception e) {
                // Log but don't fail if shortcut parsing fails
                System.err.println(
                        "Failed to parse shortcut '"
                                + shortcut
                                + "' for action "
                                + action.getClass().getSimpleName());
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
        String tooltip = action.getTooltip();
        if (tooltip != null && !tooltip.isEmpty()) {
            putValue(SHORT_DESCRIPTION, tooltip);
        }
    }

    /**
     * Get the wrapped core action.
     *
     * @return the core action
     */
    public Action getCoreAction() {
        return action;
    }

    /**
     * Parse a shortcut string like "ctrl+S" into a KeyStroke.
     *
     * @param shortcut the shortcut string
     * @return the KeyStroke, or null if parsing fails
     */
    private KeyStroke parseShortcut(String shortcut) {
        // Simple parsing - could be enhanced
        // Format: "ctrl+shift+S" or "cmd+S" or "alt+F4"

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
