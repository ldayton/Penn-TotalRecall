package core.events;

import lombok.NonNull;

/**
 * Event for requesting a screen seek operation. This eliminates direct UI access from business
 * logic actions.
 */
public record SeekScreenEvent(@NonNull Direction direction) {
    public enum Direction {
        FORWARD,
        BACKWARD
    }
}
