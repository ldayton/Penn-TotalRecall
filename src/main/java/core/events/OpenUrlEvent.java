package core.events;

import lombok.NonNull;

/**
 * Request to open a URL in the system browser.
 *
 * <p>The UI layer handles the actual browser launching using platform-specific methods.
 */
public record OpenUrlEvent(@NonNull String url, @NonNull String fallbackMessage) {}
