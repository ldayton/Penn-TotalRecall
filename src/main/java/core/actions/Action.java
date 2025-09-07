package core.actions;

/**
 * Core action interface that is UI-framework agnostic.
 *
 * <p>Actions represent commands that can be executed in the application. They provide metadata for
 * UI binding but have no dependencies on Swing or any other UI framework.
 */
public interface Action {
    /** Execute the action's command. */
    void execute();

    /**
     * Check if this action is currently enabled.
     *
     * @return true if the action can be executed
     */
    default boolean isEnabled() {
        return true;
    }

    /**
     * Get the display label for this action.
     *
     * @return the action's label for UI display
     */
    default String getLabel() {
        // Default to simple class name without "Action" suffix
        String name = getClass().getSimpleName();
        return name.endsWith("Action") ? name.substring(0, name.length() - 6) : name;
    }

    /**
     * Get the tooltip text for this action.
     *
     * @return tooltip text, or empty string if none
     */
    default String getTooltip() {
        return "";
    }

    /**
     * Get the keyboard shortcut for this action.
     *
     * @return shortcut string like "ctrl+S" or null if no shortcut
     */
    default String getShortcut() {
        return null;
    }

    /**
     * Get the menu category for this action.
     *
     * @return menu category like "File", "Edit", "Audio", or null for no menu
     */
    default String getMenuCategory() {
        return null;
    }

    /**
     * Get the sort order within the menu category.
     *
     * @return sort order (lower numbers appear first)
     */
    default int getMenuOrder() {
        return 100;
    }
}
