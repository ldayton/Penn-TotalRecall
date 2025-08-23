package control;

/** Event fired when an error dialog should be shown. */
public class ErrorRequestedEvent {
    private final String message;
    private final long timestamp;

    public ErrorRequestedEvent(String message) {
        this.message = message;
        this.timestamp = System.currentTimeMillis();
    }

    public String getMessage() {
        return message;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
