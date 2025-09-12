package core.events;

import lombok.NonNull;

/** Event fired when a dialog should be shown to the user. */
public record DialogEvent(@NonNull String message, @NonNull Type type) {
    public enum Type {
        INFO,
        ERROR
    }
}
