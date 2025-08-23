package control;

/** Event fired when focus is requested on the main window. */
public class FocusRequestedEvent {
    private final long timestamp;

    public FocusRequestedEvent() {
        this.timestamp = System.currentTimeMillis();
    }

    public long getTimestamp() {
        return timestamp;
    }
}
