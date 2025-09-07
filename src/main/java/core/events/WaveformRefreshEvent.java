package core.events;

/**
 * Event for controlling waveform display refresh behavior. This eliminates direct UI control from
 * business logic.
 */
public class WaveformRefreshEvent {
    public enum Type {
        START,
        STOP
    }

    private final Type type;

    public WaveformRefreshEvent(Type type) {
        this.type = type;
    }

    public Type getType() {
        return type;
    }
}
