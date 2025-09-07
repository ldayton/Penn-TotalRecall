package core.events;

import lombok.NonNull;

/** Event fired when an error dialog should be shown. */
public record DialogErrorEvent(@NonNull String message) {}
