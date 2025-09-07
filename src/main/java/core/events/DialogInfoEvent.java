package core.events;

import lombok.NonNull;

/** Event fired when an info dialog should be shown. */
public record DialogInfoEvent(@NonNull String message) {}
