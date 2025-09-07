package core.events;

/** Event fired when an info dialog should be shown. */
public class InfoRequestedEvent {
    private final String message;

    public InfoRequestedEvent(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
