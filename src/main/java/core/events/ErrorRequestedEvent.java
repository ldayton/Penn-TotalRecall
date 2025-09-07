package core.events;

/** Event fired when an error dialog should be shown. */
public class ErrorRequestedEvent {
    private final String message;

    public ErrorRequestedEvent(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
