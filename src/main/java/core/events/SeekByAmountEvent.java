package core.events;

/**
 * Event to request seeking by a relative amount in the audio. The audio session manager handles the
 * actual frame calculations.
 */
public record SeekByAmountEvent(Direction direction, int milliseconds) {

    public enum Direction {
        FORWARD,
        BACKWARD
    }

    public SeekByAmountEvent {
        if (milliseconds < 0) {
            throw new IllegalArgumentException("Milliseconds must be non-negative");
        }
    }
}
