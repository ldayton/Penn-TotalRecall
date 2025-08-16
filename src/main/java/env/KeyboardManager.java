package env;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import javax.swing.KeyStroke;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages keyboard shortcuts and platform-specific key formatting.
 *
 * <p>Handles:
 *
 * <ul>
 *   <li>Keyboard shortcut formatting for display according to platform conventions
 *   <li>Key symbol resolution (Mac symbols vs PC text)
 *   <li>External to internal key form conversion for shortcut parsing
 *   <li>XML keyname translation for actions.xml processing
 *   <li>Menu key determination and platform-specific behavior
 * </ul>
 *
 * <p>This class is injectable and provides platform-appropriate keyboard handling throughout the
 * application.
 */
@Singleton
public class KeyboardManager {
    private static final Logger logger = LoggerFactory.getLogger(KeyboardManager.class);

    private final Environment environment;
    private final int menuKey;

    @Inject
    public KeyboardManager(@NonNull Environment environment) {
        this.environment = environment;
        this.menuKey = computeMenuKey();
    }

    /**
     * Gets the platform-specific menu key modifier mask.
     *
     * @return the menu key modifier (Command on Mac, Ctrl on Windows/Linux)
     */
    public int getMenuKey() {
        return menuKey;
    }

    /**
     * Gets the display symbol for a key name (used by Shortcut class).
     *
     * @param key the key name
     * @return the platform-appropriate display symbol
     */
    public String getKeySymbol(String key) {
        if (environment.getPlatform() == Platform.MACOS) {
            return getMacKeySymbol(key);
        } else {
            return getPcKeySymbol(key);
        }
    }

    /**
     * Converts external key form to internal form (used by Shortcut class).
     *
     * @param externalKey the external key representation
     * @return the internal KeyStroke format
     */
    public String externalToInternalForm(String externalKey) {
        // This method converts display names back to internal KeyStroke forms
        // Used when parsing shortcuts from external configuration
        return switch (environment.getPlatform()) {
            case MACOS -> macExternalToInternal(externalKey);
            case WINDOWS, LINUX -> pcExternalToInternal(externalKey);
        };
    }

    /**
     * Formats a keyboard shortcut for display according to platform conventions.
     *
     * @param stroke the KeyStroke to format
     * @return the formatted shortcut string
     */
    public String formatShortcut(KeyStroke stroke) {
        return switch (environment.getPlatform()) {
            case MACOS -> formatMacShortcut(stroke);
            case WINDOWS, LINUX -> formatPcShortcut(stroke);
        };
    }

    /**
     * Gets the platform-specific key separator for shortcut display.
     *
     * @return empty string for Mac, "+" for Windows/Linux
     */
    public String getKeySeparator() {
        return switch (environment.getPlatform()) {
            case MACOS -> "";
            case WINDOWS, LINUX -> "+";
        };
    }

    /**
     * Gets the platform-specific key order for modifier display.
     *
     * @return the ordered list of modifier symbols/names
     */
    public List<String> getKeyOrder() {
        return switch (environment.getPlatform()) {
            case MACOS -> List.of("^", "⌥", "⇧", "⌘");
            case WINDOWS, LINUX -> List.of("Shift", "Ctrl", "Alt");
        };
    }

    /**
     * Converts XML keynames from actions.xml to internal KeyStroke format. This is separate from
     * display formatting and specifically handles the XML schema.
     *
     * @param xmlKeyname the XML key name
     * @return the internal KeyStroke format
     */
    public String xmlKeynameToInternalForm(String xmlKeyname) {
        // Handle cross-platform "menu" modifier: Command on Mac, Ctrl on Windows/Linux
        if ("menu".equals(xmlKeyname)) {
            return switch (environment.getPlatform()) {
                case MACOS -> "meta";
                case WINDOWS, LINUX -> "ctrl";
            };
        }

        // Handle other common XML keynames to internal format
        return switch (xmlKeyname.toLowerCase()) {
            case "alt" -> "alt";
            case "shift" -> "shift";
            case "ctrl" -> "ctrl";
            case "command" -> "meta"; // Explicit command for Mac
                // Key names (non-modifiers) - pass through uppercase
            default -> xmlKeyname.toUpperCase();
        };
    }

    /**
     * Whether this platform should show Emacs keybinding preferences. Mac has its own keybinding
     * conventions.
     *
     * @return false for Mac, true for other platforms
     */
    public boolean shouldShowEmacsKeybindingOption() {
        return environment.getPlatform() != Platform.MACOS;
    }

    // =============================================================================
    // PRIVATE IMPLEMENTATION METHODS
    // =============================================================================

    private String formatMacShortcut(KeyStroke stroke) {
        // Implement Mac-specific shortcut formatting with symbols
        String result = "";
        int modifiers = stroke.getModifiers();

        if ((modifiers & InputEvent.CTRL_DOWN_MASK) != 0) result += "^";
        if ((modifiers & InputEvent.ALT_DOWN_MASK) != 0) result += "⌥";
        if ((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0) result += "⇧";
        if ((modifiers & InputEvent.META_DOWN_MASK) != 0) result += "⌘";

        // Add key symbol
        String keySymbol = getMacKeySymbol(stroke.getKeyCode());
        if (keySymbol != null) {
            result += keySymbol;
        } else {
            result +=
                    KeyStroke.getKeyStroke(stroke.getKeyCode(), 0)
                            .toString()
                            .replace("pressed ", "");
        }

        return result;
    }

    private String formatPcShortcut(KeyStroke stroke) {
        // Implement PC-specific shortcut formatting with text
        String result = "";
        int modifiers = stroke.getModifiers();

        if ((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0) result += "Shift+";
        if ((modifiers & InputEvent.CTRL_DOWN_MASK) != 0) result += "Ctrl+";
        if ((modifiers & InputEvent.ALT_DOWN_MASK) != 0) result += "Alt+";

        // Add key name
        String keyName = getPcKeySymbol(stroke.getKeyCode());
        if (keyName != null) {
            result += keyName;
        } else {
            result +=
                    KeyStroke.getKeyStroke(stroke.getKeyCode(), 0)
                            .toString()
                            .replace("pressed ", "");
        }

        return result;
    }

    private String getMacKeySymbol(int keyCode) {
        return switch (keyCode) {
            case KeyEvent.VK_BACK_SPACE -> "⌫";
            case KeyEvent.VK_DELETE -> "⌦";
            case KeyEvent.VK_ENTER -> "↩";
            case KeyEvent.VK_ESCAPE -> "⎋";
            case KeyEvent.VK_HOME -> "\u2196";
            case KeyEvent.VK_END -> "\u2198";
            case KeyEvent.VK_PAGE_UP -> "PgUp";
            case KeyEvent.VK_PAGE_DOWN -> "PgDn";
            case KeyEvent.VK_LEFT -> "←";
            case KeyEvent.VK_RIGHT -> "→";
            case KeyEvent.VK_UP -> "↑";
            case KeyEvent.VK_DOWN -> "↓";
            case KeyEvent.VK_TAB -> "Tab";
            default -> null;
        };
    }

    private String getPcKeySymbol(int keyCode) {
        return switch (keyCode) {
            case KeyEvent.VK_BACK_SPACE -> "BackSpace";
            case KeyEvent.VK_DELETE -> "Del";
            case KeyEvent.VK_ENTER -> "Enter";
            case KeyEvent.VK_ESCAPE -> "Esc";
            case KeyEvent.VK_HOME -> "Home";
            case KeyEvent.VK_END -> "End";
            case KeyEvent.VK_PAGE_UP -> "PgUp";
            case KeyEvent.VK_PAGE_DOWN -> "PgDn";
            case KeyEvent.VK_LEFT -> "Left";
            case KeyEvent.VK_RIGHT -> "Right";
            case KeyEvent.VK_UP -> "Up";
            case KeyEvent.VK_DOWN -> "Down";
            case KeyEvent.VK_TAB -> "Tab";
            default -> null;
        };
    }

    private String getMacKeySymbol(String key) {
        // Returns Mac symbols for keys, null if no specific symbol
        return switch (key.toLowerCase()) {
            case "cmd", "meta" -> "⌘";
            case "option", "alt" -> "⌥";
            case "shift" -> "⇧";
            case "ctrl", "control" -> "^";
            case "tab" -> "⇥";
            case "enter", "return" -> "↩";
            case "delete" -> "⌫";
            case "escape" -> "⎋";
            case "up" -> "↑";
            case "down" -> "↓";
            case "left" -> "←";
            case "right" -> "→";
            default -> null; // Use default key name
        };
    }

    private String getPcKeySymbol(String key) {
        // PC uses text-based key names, no special symbols
        return switch (key.toLowerCase()) {
            case "cmd", "meta" -> "Win";
            case "option" -> "Alt";
            case "alt" -> "Alt";
            case "shift" -> "Shift";
            case "ctrl", "control" -> "Ctrl";
            case "tab" -> "Tab";
            case "enter", "return" -> "Enter";
            case "delete" -> "Del";
            case "space" -> "Space";
            case "escape" -> "Esc";
            case "up" -> "↑";
            case "down" -> "↓";
            case "left" -> "←";
            case "right" -> "→";
            default -> key; // Use key name as-is
        };
    }

    private String macExternalToInternal(String externalKey) {
        // Convert Mac display symbols back to internal KeyStroke format
        return switch (externalKey.toLowerCase()) {
            case "⌘" -> "meta";
            case "⌥" -> "alt";
            case "⇧" -> "shift";
            case "^" -> "ctrl";
            case "⇥" -> "TAB";
            case "↩" -> "ENTER";
            case "⌫" -> "DELETE";
            case "⎋" -> "ESCAPE";
            case "↑" -> "UP";
            case "↓" -> "DOWN";
            case "←" -> "LEFT";
            case "→" -> "RIGHT";
            case "menu" -> "meta"; // Cross-platform "menu" = Command on Mac
            case "command", "cmd" -> "meta";
            case "option" -> "alt";
            case "control", "ctrl" -> "ctrl";
            case "shift" -> "shift";
            case "space" -> "SPACE";
            case "tab" -> "TAB";
            case "enter", "return" -> "ENTER";
            case "delete" -> "DELETE";
            case "escape", "esc" -> "ESCAPE";
            default -> externalKey.toUpperCase(); // Uppercase key names (A, B, C, etc.)
        };
    }

    private String pcExternalToInternal(String externalKey) {
        // Convert PC display text back to internal KeyStroke format
        return switch (externalKey.toLowerCase()) {
            case "menu" -> "ctrl"; // Cross-platform "menu" = Ctrl on Windows/Linux
            case "command" -> "meta"; // Command key maps to meta on PC too
            case "win" -> "meta";
            case "alt" -> "alt";
            case "shift" -> "shift";
            case "ctrl" -> "ctrl";
            case "tab" -> "TAB";
            case "enter" -> "ENTER";
            case "del" -> "DELETE";
            case "space" -> "SPACE";
            case "esc" -> "ESCAPE";
            case "↑" -> "UP";
            case "↓" -> "DOWN";
            case "←" -> "LEFT";
            case "→" -> "RIGHT";
            default -> externalKey.toUpperCase(); // Use as-is for regular keys
        };
    }

    private int computeMenuKey() {
        try {
            // Try to get the platform-specific menu key
            if (java.awt.GraphicsEnvironment.isHeadless()) {
                // In headless environment, return platform-appropriate default
                return switch (environment.getPlatform()) {
                    case MACOS -> InputEvent.META_DOWN_MASK;
                    case WINDOWS, LINUX -> InputEvent.CTRL_DOWN_MASK;
                };
            } else {
                return Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
            }
        } catch (Exception e) {
            logger.warn("Failed to get menu shortcut key, using platform default", e);
            // Fall back to platform-appropriate default
            return switch (environment.getPlatform()) {
                case MACOS -> InputEvent.META_DOWN_MASK;
                case WINDOWS, LINUX -> InputEvent.CTRL_DOWN_MASK;
            };
        }
    }
}
