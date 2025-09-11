package ui.adapters;

import java.util.Optional;
import lombok.NonNull;
import ui.shortcuts.Shortcut;

/**
 * Swing-specific action configuration that uses Swing types. This is what UI components should work
 * with, not core.actions.ActionConfig.
 */
public record SwingActionConfig(
        @NonNull String className,
        @NonNull String name,
        Optional<String> tooltip,
        Optional<Shortcut> shortcut) {

    public Optional<String> arg() {
        return Optional.empty();
    }
}
