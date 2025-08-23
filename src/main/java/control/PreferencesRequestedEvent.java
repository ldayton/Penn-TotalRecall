package control;

/** Event fired when the user requests to open preferences. */
public class PreferencesRequestedEvent {
    private final long timestamp;

    public PreferencesRequestedEvent() {
        this.timestamp = System.currentTimeMillis();
    }

    public long getTimestamp() {
        return timestamp;
    }
}
