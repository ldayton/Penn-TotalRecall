package control;

import java.util.List;

/** Base class for PC platforms (Windows, Linux) with shared behavior. */
public abstract class PCPlatform implements PlatformProvider {

    @Override
    public String getKeySeparator() {
        return "+";
    }

    @Override
    public String getKeySymbol(String internalKey) {
        return switch (internalKey) {
            case "control", "ctrl" -> "Ctrl";
            case "alt" -> "Alt";
            case "shift" -> "Shift";
            case "meta" -> "Meta";
            case "BACK_SPACE" -> "BackSpace";
            case "DELETE" -> "Del";
            case "ENTER" -> "Enter";
            case "ESCAPE" -> "Esc";
            case "HOME" -> "Home";
            case "END" -> "End";
            case "PAGE_UP" -> "PgUp";
            case "PAGE_DOWN" -> "PgDn";
            case "LEFT" -> "Left";
            case "RIGHT" -> "Right";
            case "UP" -> "Up";
            case "DOWN" -> "Down";
            case "TAB" -> "Tab";
            default -> null;
        };
    }

    @Override
    public List<String> getKeyOrder() {
        return List.of("Shift", "Ctrl", "Alt");
    }

    @Override
    public String externalToInternalForm(String externalKey) {
        return switch (externalKey) {
            case "menu" -> "ctrl";
            case "command" -> "meta";
            default -> externalKey;
        };
    }
}
