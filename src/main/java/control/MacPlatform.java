package control;

import java.util.List;

public class MacPlatform implements PlatformProvider {

    @Override
    public String getKeySeparator() {
        return "";
    }

    @Override
    public String getKeySymbol(String internalKey) {
        return switch (internalKey) {
            case "control", "ctrl" -> "^";
            case "alt" -> "⌥";
            case "shift" -> "⇧";
            case "meta" -> "⌘";
            case "BACK_SPACE" -> "⌫";
            case "DELETE" -> "⌦";
            case "ENTER" -> "↩";
            case "ESCAPE" -> "⎋";
            case "HOME" -> "\u2196";
            case "END" -> "\u2198";
            case "PAGE_UP" -> "PgUp";
            case "PAGE_DOWN" -> "PgDn";
            case "LEFT" -> "←";
            case "RIGHT" -> "→";
            case "UP" -> "↑";
            case "DOWN" -> "↓";
            case "TAB" -> "Tab";
            default -> null;
        };
    }

    @Override
    public List<String> getKeyOrder() {
        return List.of("^", "⌥", "⇧", "⌘");
    }

    @Override
    public String externalToInternalForm(String externalKey) {
        return switch (externalKey) {
            case "menu" -> "meta";
            case "command" -> "meta";
            default -> externalKey;
        };
    }
}
