package core.actions;

import java.util.Optional;
import lombok.NonNull;

public record ActionConfig(
        @NonNull String className,
        @NonNull String name,
        Optional<String> tooltip,
        Optional<ShortcutSpec> shortcut) {

    public Optional<String> arg() {
        return Optional.empty();
    }
}
