package shortcuts;

import static java.util.stream.Collectors.joining;

import env.Environment;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import javax.swing.KeyStroke;

public class Shortcut {
    private static final Set<String> ACTION_WORDS = Set.of("typed", "pressed", "released");

    public final KeyStroke stroke;
    private final String internalForm;
    private final Environment env;

    private static final String INTERNAL_FORM_DELIMITER = " ";

    public Shortcut(KeyStroke stroke, Environment env) {
        this.stroke = Objects.requireNonNull(stroke);
        this.env = Objects.requireNonNull(env);
        this.internalForm = ModernKeyUtils.getInternalForm(stroke);
    }

    // Factory methods for convenience
    public static Shortcut forCurrentPlatform(KeyStroke stroke) {
        Objects.requireNonNull(stroke);
        return new Shortcut(stroke, Environment.getInstance());
    }

    public static Shortcut forPlatform(KeyStroke stroke, Environment env) {
        Objects.requireNonNull(stroke);
        Objects.requireNonNull(env);
        return new Shortcut(stroke, env);
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
                                    String symbol = env.getKeySymbol(key);
                                    String displayKey = symbol != null ? symbol : key;
                                    return isSymbol(displayKey)
                                            ? displayKey
                                            : capitalizeKey(displayKey);
                                })
                        .toList();

        List<String> keyOrder = env.getKeyOrder();
        return Stream.concat(
                        keyOrder.stream().filter(keys::contains),
                        keys.stream().filter(key -> !keyOrder.contains(key)))
                .collect(joining(env.getKeySeparator()));
    }

    private boolean isSymbol(String key) {
        Objects.requireNonNull(key);
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

    private String capitalizeKey(String key) {
        Objects.requireNonNull(key);
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

    public static Shortcut fromInternalForm(String internalForm) {
        Objects.requireNonNull(internalForm);
        KeyStroke stroke = KeyStroke.getKeyStroke(internalForm);
        if (stroke == null) {
            throw new RuntimeException("Cannot parse keystroke: " + internalForm);
        }
        return new Shortcut(stroke, Environment.getInstance());
    }

    public static Shortcut fromExternalForm(
            List<String> maskKeyExternalForms,
            List<String> nonMaskKeyExternalForms,
            Environment env) {
        Objects.requireNonNull(maskKeyExternalForms);
        Objects.requireNonNull(nonMaskKeyExternalForms);
        Objects.requireNonNull(env);
        String internalShortcutForm =
                Stream.concat(
                                maskKeyExternalForms.stream().map(env::externalToInternalForm),
                                nonMaskKeyExternalForms.stream().map(env::externalToInternalForm))
                        .collect(joining(INTERNAL_FORM_DELIMITER));
        KeyStroke stroke = KeyStroke.getKeyStroke(internalShortcutForm);
        if (stroke == null) {
            throw new RuntimeException("Cannot parse keystroke: " + internalShortcutForm);
        }
        return new Shortcut(stroke, env);
    }

    public static Shortcut fromExternalForm(
            List<String> maskKeyExternalForms, List<String> nonMaskKeyExternalForms) {
        Objects.requireNonNull(maskKeyExternalForms);
        Objects.requireNonNull(nonMaskKeyExternalForms);
        return fromExternalForm(
                maskKeyExternalForms, nonMaskKeyExternalForms, Environment.getInstance());
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
