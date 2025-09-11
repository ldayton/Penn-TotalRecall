package core.actions;

import java.util.Set;
import lombok.NonNull;

public record ShortcutSpec(@NonNull Set<String> modifiers, @NonNull String key) {}
