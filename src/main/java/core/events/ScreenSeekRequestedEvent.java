package core.events;

/**
 * Event for requesting a screen seek operation. This eliminates direct UI access from business
 * logic actions.
 */
public class ScreenSeekRequestedEvent {
    public enum Direction {
        FORWARD,
        BACKWARD
    }

    private final Direction direction;

    public ScreenSeekRequestedEvent(Direction direction) {
        this.direction = direction;
    }

    public Direction getDirection() {
        return direction;
    }
}
