package control;

/** Event fired when an info dialog should be shown. */
public class InfoRequestedEvent {
    private final String message;
    private final long timestamp;

    public InfoRequestedEvent(String message) {
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
