package events;

/** Event fired when the user requests to exit the application. */
public class ExitRequestedEvent {
    private final long timestamp;

    public ExitRequestedEvent() {
        this.timestamp = System.currentTimeMillis();
    }

    public long getTimestamp() {
        return timestamp;
    }
}
