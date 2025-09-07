package core.events;

import lombok.Getter;

/** Event published when a move plus replay last 200ms is requested. */
@Getter
public class Last200PlusMoveRequestedEvent {
    private final boolean forward;

    public Last200PlusMoveRequestedEvent(boolean forward) {
        this.forward = forward;
    }
}
