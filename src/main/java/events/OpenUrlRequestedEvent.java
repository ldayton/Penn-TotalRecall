package events;

import lombok.Getter;

/**
 * Request to open a URL in the system browser.
 *
 * <p>The UI layer handles the actual browser launching using platform-specific methods.
 */
@Getter
public class OpenUrlRequestedEvent {
    private final String url;
    private final String fallbackMessage;

    /**
     * Create a request to open a URL.
     *
     * @param url the URL to open
     * @param fallbackMessage message to show if browser cannot be launched
     */
    public OpenUrlRequestedEvent(String url, String fallbackMessage) {
        this.url = url;
        this.fallbackMessage = fallbackMessage;
    }

    /**
     * Create a request to open a URL with a default fallback message.
     *
     * @param url the URL to open
     */
    public OpenUrlRequestedEvent(String url) {
        this(url, "Please visit: " + url);
    }
}
