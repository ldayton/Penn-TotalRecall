package ui;

import core.env.Platform;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.util.List;
import lombok.Getter;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages keyboard shortcuts and platform-specific key formatting.
 *
 * <p>Handles shortcut display formatting, key symbol resolution, and cross-platform menu key
 * determination for consistent keyboard behavior across macOS, Windows, and Linux.
 */
@Singleton
public class KeyboardManager {
    private static final Logger logger = LoggerFactory.getLogger(KeyboardManager.class);

    private final Platform platform;

    /**
     * -- GETTER -- Gets the platform-specific menu key modifier mask. (Command on Mac, Ctrl on
     * Windows/Linux)
     */
    @Getter private final int menuKey;

    @Inject
    public KeyboardManager(@NonNull Platform platform) {
        this.platform = platform;
        this.menuKey = computeMenuKey();
    }

    /**
     * Gets the display symbol for a key name (used by Shortcut class).
     *
     * @param key the key name
     * @return the platform-appropriate display symbol
     */
    public String getKeySymbol(@NonNull String key) {
        if (platform.detect() == Platform.PlatformType.MACOS) {
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
    public String externalToInternalForm(@NonNull String externalKey) {
        // This method converts display names back to internal KeyStroke forms
        // Used when parsing shortcuts from external configuration
        return switch (platform.detect()) {
            case MACOS -> macExternalToInternal(externalKey);
            case WINDOWS, LINUX -> pcExternalToInternal(externalKey);
        };
    }

    /**
     * Gets the platform-specific key separator for shortcut display.
     *
     * @return empty string for Mac, "+" for Windows/Linux
     */
    public String getKeySeparator() {
        return switch (platform.detect()) {
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
        return switch (platform.detect()) {
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
    public String xmlKeynameToInternalForm(@NonNull String xmlKeyname) {
        // Handle cross-platform "menu" modifier: Command on Mac, Ctrl on Windows/Linux
        if ("menu".equals(xmlKeyname)) {
            return switch (platform.detect()) {
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
        return platform.detect() != Platform.PlatformType.MACOS;
    }

    private String getMacKeySymbol(String key) {
        // Returns Mac symbols for keys, null if no specific symbol
        return switch (key.toLowerCase()) {
            case "equals" -> "="; // Display Cmd+= as ⌘=
            case "add" -> "+"; // Numpad add
            case "minus" -> "-"; // Display Cmd+- as ⌘-
            case "subtract" -> "-"; // Numpad subtract
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
            case "equals" -> "="; // Show = for the equals key
            case "add" -> "+"; // Numpad add
            case "minus" -> "-"; // Show - for minus key
            case "subtract" -> "-"; // Numpad subtract
            case "cmd", "meta" -> "Win";
            case "option", "alt" -> "Alt";
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
            case "⌘", "command", "cmd", "menu" -> "meta"; // Cross-platform "menu" = Command on Mac
            case "⌥", "option" -> "alt";
            case "⇧", "shift" -> "shift";
            case "^", "control", "ctrl" -> "ctrl";
            case "⇥", "tab" -> "TAB";
            case "↩", "enter", "return" -> "ENTER";
            case "⌫", "delete" -> "DELETE";
            case "⎋", "escape", "esc" -> "ESCAPE";
            case "↑" -> "UP";
            case "↓" -> "DOWN";
            case "←" -> "LEFT";
            case "→" -> "RIGHT";
            case "space" -> "SPACE";
            default -> externalKey.toUpperCase(); // Uppercase key names (A, B, C, etc.)
        };
    }

    private String pcExternalToInternal(String externalKey) {
        // Convert PC display text back to internal KeyStroke format
        return switch (externalKey.toLowerCase()) {
            case "menu" -> "ctrl"; // Cross-platform "menu" = Ctrl on Windows/Linux
            case "command", "win" -> "meta"; // Command key maps to meta on PC too
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
            if (GraphicsEnvironment.isHeadless()) {
                // In headless environment, return platform-appropriate default
                return switch (platform.detect()) {
                    case MACOS -> InputEvent.META_DOWN_MASK;
                    case WINDOWS, LINUX -> InputEvent.CTRL_DOWN_MASK;
                };
            } else {
                return Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
            }
        } catch (Exception e) {
            logger.warn("Failed to get menu shortcut key, using platform default", e);
            // Fall back to platform-appropriate default
            return switch (platform.detect()) {
                case MACOS -> InputEvent.META_DOWN_MASK;
                case WINDOWS, LINUX -> InputEvent.CTRL_DOWN_MASK;
            };
        }
    }
}
