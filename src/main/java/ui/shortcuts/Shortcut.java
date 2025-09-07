package ui.shortcuts;

import static java.util.stream.Collectors.joining;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import javax.swing.KeyStroke;
import lombok.NonNull;
import ui.KeyboardManager;

public class Shortcut {
    private static final Set<String> ACTION_WORDS = Set.of("typed", "pressed", "released");

    public final KeyStroke stroke;
    private final String internalForm;
    private final KeyboardManager keyboardManager;

    private static final String INTERNAL_FORM_DELIMITER = " ";

    public Shortcut(@NonNull KeyStroke stroke, @NonNull KeyboardManager keyboardManager) {
        this.stroke = stroke;
        this.keyboardManager = keyboardManager;
        this.internalForm = ModernKeyUtils.getInternalForm(stroke);
    }

    // Factory methods for convenience

    public static Shortcut forPlatform(
            @NonNull KeyStroke stroke, @NonNull KeyboardManager keyboardManager) {
        return new Shortcut(stroke, keyboardManager);
    }

    public String getInternalForm() {
        return internalForm;
    }

    @Override
    public String toString() {
        List<String> keys =
                Stream.of(internalForm.split(INTERNAL_FORM_DELIMITER))
                        .filter(key -> !ACTION_WORDS.contains(key))
                        .map(
                                key -> {
                                    String symbol = keyboardManager.getKeySymbol(key);
                                    String displayKey = symbol != null ? symbol : key;
                                    return isSymbol(displayKey)
                                            ? displayKey
                                            : capitalizeKey(displayKey);
                                })
                        .toList();

        List<String> keyOrder = keyboardManager.getKeyOrder();
        return Stream.concat(
                        keyOrder.stream().filter(keys::contains),
                        keys.stream().filter(key -> !keyOrder.contains(key)))
                .collect(joining(keyboardManager.getKeySeparator()));
    }

    private boolean isSymbol(@NonNull String key) {
        // Check if this is a Unicode symbol (Mac keys) or already-formatted PC key
        return (key.length() == 1
                        && (key.equals("⌘")
                                || key.equals("⌥")
                                || key.equals("⇧")
                                || key.equals("^")
                                || key.equals("↩")
                                || key.equals("⌫")
                                || key.equals("⌦")
                                || key.equals("⎋")
                                || key.equals("←")
                                || key.equals("→")
                                || key.equals("↑")
                                || key.equals("↓")))
                ||
                // PC platform pre-formatted keys that shouldn't be capitalized
                (key.equals("PgUp")
                        || key.equals("PgDn")
                        || key.equals("BackSpace")
                        || key.equals("Del")
                        || key.equals("Enter")
                        || key.equals("Esc")
                        || key.equals("Home")
                        || key.equals("End")
                        || key.equals("Left")
                        || key.equals("Right")
                        || key.equals("Up")
                        || key.equals("Down")
                        || key.equals("Tab")
                        || key.equals("Ctrl")
                        || key.equals("Alt")
                        || key.equals("Shift")
                        || key.equals("Meta"));
    }

    private String capitalizeKey(@NonNull String key) {
        return switch (key) {
            case "" -> key;
            case "SPACE" -> "Space";
            case "PAGE_UP" -> "PgUp";
            case "PAGE_DOWN" -> "PgDn";
            case String s when s.startsWith("NUMPAD") && s.length() == 7 -> "Num " + s.charAt(6);
            default -> {
                String lower = key.toLowerCase(Locale.ROOT);
                yield Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
            }
        };
    }

    public static Shortcut fromInternalForm(@NonNull String internalForm) {
        KeyStroke stroke = KeyStroke.getKeyStroke(internalForm);
        if (stroke == null) {
            throw new RuntimeException("Cannot parse keystroke: " + internalForm);
        }
        KeyboardManager keyboardManager =
                app.swing.SwingApp.getInjectedInstance(KeyboardManager.class);
        return new Shortcut(stroke, keyboardManager);
    }

    public static Shortcut fromExternalForm(
            @NonNull List<String> maskKeyExternalForms,
            @NonNull List<String> nonMaskKeyExternalForms) {
        KeyboardManager keyboardManager =
                app.swing.SwingApp.getInjectedInstance(KeyboardManager.class);
        String internalShortcutForm =
                Stream.concat(
                                maskKeyExternalForms.stream()
                                        .map(keyboardManager::externalToInternalForm),
                                nonMaskKeyExternalForms.stream()
                                        .map(keyboardManager::externalToInternalForm))
                        .collect(joining(INTERNAL_FORM_DELIMITER));
        KeyStroke stroke = KeyStroke.getKeyStroke(internalShortcutForm);
        if (stroke == null) {
            throw new RuntimeException("Cannot parse keystroke: " + internalShortcutForm);
        }
        return new Shortcut(stroke, keyboardManager);
    }

    /**
     * Creates a Shortcut from XML keynames specifically for actions.xml parsing. This method uses
     * XML-specific conversion separate from display formatting.
     */
    public static Shortcut fromXmlForm(
            @NonNull List<String> maskKeyXmlNames, @NonNull List<String> nonMaskKeyXmlNames) {

        KeyboardManager keyboardManager =
                app.swing.SwingApp.getInjectedInstance(KeyboardManager.class);
        String internalShortcutForm =
                Stream.concat(
                                maskKeyXmlNames.stream()
                                        .map(keyboardManager::xmlKeynameToInternalForm),
                                nonMaskKeyXmlNames.stream()
                                        .map(keyboardManager::xmlKeynameToInternalForm))
                        .collect(joining(INTERNAL_FORM_DELIMITER));
        KeyStroke stroke = KeyStroke.getKeyStroke(internalShortcutForm);
        if (stroke == null) {
            throw new RuntimeException("Cannot parse keystroke: " + internalShortcutForm);
        }
        return new Shortcut(stroke, keyboardManager);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Shortcut shortcut)) {
            return false;
        }
        return Objects.equals(stroke, shortcut.stroke);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stroke);
    }
}
