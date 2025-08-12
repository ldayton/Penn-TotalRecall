package shortcuts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javax.swing.KeyStroke;

public class Shortcut {
    public final KeyStroke stroke;
    private final String internalForm;

    private static final boolean IS_MAC =
            System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac");
    private static final String INTERNAL_FORM_DELIMITER = " ";

    // PC key names
    private static final String PC_CTRL = "Ctrl";
    private static final String PC_ALT = "Alt";
    private static final String PC_SHIFT = "Shift";
    private static final String PC_META = "Meta";

    // Mac key symbols
    private static final String MAC_CTRL = "^";
    private static final String MAC_OPTION = "⌥";
    private static final String MAC_SHIFT = "⇧";
    private static final String MAC_COMMAND = "⌘";

    private static final String SYS_SEP = IS_MAC ? "" : "+";

    private static final Map<String, KeyMapping> MAC_MAP = createMacMap();
    private static final List<String> MAC_ORDER =
            List.of(MAC_CTRL, MAC_OPTION, MAC_SHIFT, MAC_COMMAND);
    private static final List<String> PC_ORDER = List.of(PC_SHIFT, PC_CTRL, PC_ALT);

    private record KeyMapping(String pc, String mac) {}

    private static Map<String, KeyMapping> createMacMap() {
        Map<String, KeyMapping> map = new HashMap<>();
        map.put("control", new KeyMapping(PC_CTRL, MAC_CTRL));
        map.put("alt", new KeyMapping(PC_ALT, MAC_OPTION));
        map.put("shift", new KeyMapping(PC_SHIFT, MAC_SHIFT));
        map.put("meta", new KeyMapping(PC_META, MAC_COMMAND));
        map.put("BACK_SPACE", new KeyMapping("BackSpace", "⌫"));
        map.put("DELETE", new KeyMapping("Del", "⌦"));
        map.put("ENTER", new KeyMapping("Enter", "↩"));
        map.put("ESCAPE", new KeyMapping("Esc", "⎋"));
        map.put("HOME", new KeyMapping("Home", "\u2196"));
        map.put("END", new KeyMapping("End", "\u2198"));
        map.put("PAGE_UP", new KeyMapping("PgUp", "PgUp"));
        map.put("PAGE_DOWN", new KeyMapping("PgDn", "PgDn"));
        map.put("LEFT", new KeyMapping("Left", "←"));
        map.put("RIGHT", new KeyMapping("Right", "→"));
        map.put("UP", new KeyMapping("Up", "↑"));
        map.put("DOWN", new KeyMapping("Down", "↓"));
        map.put("TAB", new KeyMapping("Tab", "Tab"));
        return map;
    }

    public Shortcut(KeyStroke stroke) {
        this.stroke = stroke;
        String internal = UnsafeKeyUtils.getInternalFormOrNull(stroke);
        if (internal == null) {
            throw new RuntimeException(
                    """
                    sorry, I refuse to create a Shortcut whose KeyStroke has no \
                    valid internalForm field according to UnsafeKeyUtils.java""");
        }
        this.internalForm = internal;
    }

    public String getInternalForm() {
        return internalForm;
    }

    private boolean sortKeys(String a, String b) {
        List<String> order = IS_MAC ? MAC_ORDER : PC_ORDER;
        int indexA = order.indexOf(a);
        int indexB = order.indexOf(b);

        if (indexA == -1 && indexB == -1) {
            return true; // arbitrary choice
        }
        if (indexA != -1 && indexB == -1) {
            return true;
        }
        if (indexA == -1 && indexB != -1) {
            return false;
        }
        return indexA < indexB;
    }

    @Override
    public String toString() {
        List<String> parts = separateInternalForm(internalForm);
        List<String> newParts = new ArrayList<>();

        for (String s : parts) {
            KeyMapping mapping = MAC_MAP.get(s);
            if (mapping != null) {
                newParts.add(IS_MAC ? mapping.mac : mapping.pc);
            } else {
                newParts.add(s);
            }
        }

        newParts.removeAll(List.of("typed", "pressed", "released"));

        // Capitalize first letter of each part
        for (int i = 0; i < newParts.size(); i++) {
            String part = newParts.get(i).toLowerCase(Locale.ROOT);
            if (!part.isEmpty()) {
                newParts.set(i, Character.toUpperCase(part.charAt(0)) + part.substring(1));
            }
        }

        // Sort keys
        newParts.sort((a, b) -> sortKeys(a, b) ? -1 : 1);

        return String.join(SYS_SEP, newParts);
    }

    public static List<String> separateInternalForm(String internalForm) {
        return List.of(internalForm.split(INTERNAL_FORM_DELIMITER));
    }

    public static Shortcut fromInternalForm(String internalForm) {
        KeyStroke stroke = KeyStroke.getKeyStroke(internalForm);
        if (stroke != null) {
            return new Shortcut(stroke);
        } else {
            System.err.println(
                    "KeyStroke.getKeyStroke could not parse allegedly internal form: "
                            + internalForm);
            return null;
        }
    }

    public static Shortcut fromExternalForm(
            List<String> maskKeyExternalForms, List<String> nonMaskKeyExternalForms) {
        List<String> maskKeyInternalForms = new ArrayList<>();
        for (String form : maskKeyExternalForms) {
            maskKeyInternalForms.add(Key.external2InternalForm(form));
        }

        List<String> nonMaskKeyInternalForms = new ArrayList<>();
        for (String form : nonMaskKeyExternalForms) {
            nonMaskKeyInternalForms.add(Key.external2InternalForm(form));
        }

        String internalShortcutForm =
                String.join(INTERNAL_FORM_DELIMITER, maskKeyInternalForms)
                        + INTERNAL_FORM_DELIMITER
                        + String.join(INTERNAL_FORM_DELIMITER, nonMaskKeyInternalForms);

        return fromInternalForm(internalShortcutForm);
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

    public static class Key {
        private static final String EXTERNAL_MENU = "menu";
        private static final String EXTERNAL_COMMAND = "command";

        public static final String INTERNAL_ALT = "alt";
        public static final String INTERNAL_CTRL = "ctrl";
        public static final String INTERNAL_ESCAPE = "ESCAPE";
        public static final String INTERNAL_META = "meta";
        public static final String INTERNAL_SHIFT = "shift";

        public static String external2InternalForm(String str) {
            return switch (str) {
                case EXTERNAL_MENU -> IS_MAC ? INTERNAL_META : INTERNAL_CTRL;
                case EXTERNAL_COMMAND -> INTERNAL_META;
                default -> str;
            };
        }
    }
}
