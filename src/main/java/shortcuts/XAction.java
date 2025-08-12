package shortcuts;

public record XAction(
        String className, String enumValue, String name, String tooltip, Shortcut shortcut) {

    public String getJavaTooltip() {
        return tooltip;
    }

    public Shortcut getJavaShortcut() {
        return shortcut;
    }

    public String getJavaEnum() {
        return enumValue;
    }

    public String getId() {
        return enumValue != null ? className + "-" + enumValue : className;
    }

    public XAction withShortcut(Shortcut newShortcut) {
        return new XAction(className, enumValue, name, tooltip, newShortcut);
    }
}
