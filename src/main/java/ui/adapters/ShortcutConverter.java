package ui.adapters;

import core.actions.ActionConfig;
import core.actions.ShortcutSpec;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Optional;
import javax.swing.KeyStroke;
import lombok.NonNull;
import ui.KeyboardManager;
import ui.shortcuts.Shortcut;

/**
 * Converts between core action types and Swing-specific types. Handles the boundary translation
 * between platform-independent core and Swing UI.
 */
@Singleton
public class ShortcutConverter {
    private final KeyboardManager keyboardManager;

    @Inject
    public ShortcutConverter(@NonNull KeyboardManager keyboardManager) {
        this.keyboardManager = keyboardManager;
    }

    public KeyStroke toKeyStroke(ShortcutSpec spec) {
        int modifiers = 0;
        for (String mod : spec.modifiers()) {
            String normalizedMod = mod.toLowerCase();
            modifiers |=
                    switch (normalizedMod) {
                        case "shift" -> java.awt.event.InputEvent.SHIFT_DOWN_MASK;
                        case "ctrl" -> java.awt.event.InputEvent.CTRL_DOWN_MASK;
                        case "alt" -> java.awt.event.InputEvent.ALT_DOWN_MASK;
                        case "meta", "cmd" -> java.awt.event.InputEvent.META_DOWN_MASK;
                        case "menu" -> keyboardManager.getMenuKey();
                        default -> 0;
                    };
        }

        String normalizedKey = spec.key().toLowerCase();
        int keyCode =
                switch (normalizedKey) {
                    case "left" -> java.awt.event.KeyEvent.VK_LEFT;
                    case "right" -> java.awt.event.KeyEvent.VK_RIGHT;
                    case "up" -> java.awt.event.KeyEvent.VK_UP;
                    case "down" -> java.awt.event.KeyEvent.VK_DOWN;
                    case "space" -> java.awt.event.KeyEvent.VK_SPACE;
                    case "enter" -> java.awt.event.KeyEvent.VK_ENTER;
                    case "escape" -> java.awt.event.KeyEvent.VK_ESCAPE;
                    case "tab" -> java.awt.event.KeyEvent.VK_TAB;
                    default -> {
                        if (normalizedKey.length() != 1) {
                            throw new IllegalArgumentException(
                                    "Invalid key specification: '"
                                            + spec.key()
                                            + "' - must be a special key name or single character");
                        }
                        yield Character.toUpperCase(normalizedKey.charAt(0));
                    }
                };

        return KeyStroke.getKeyStroke(keyCode, modifiers);
    }

    public SwingActionConfig toSwingConfig(ActionConfig coreConfig) {
        Optional<Shortcut> swingShortcut = Optional.empty();
        if (coreConfig.shortcut().isPresent()) {
            KeyStroke stroke = toKeyStroke(coreConfig.shortcut().get());
            swingShortcut = Optional.of(Shortcut.forPlatform(stroke, keyboardManager));
        }

        return new SwingActionConfig(
                coreConfig.className(), coreConfig.name(), coreConfig.tooltip(), swingShortcut);
    }
}
