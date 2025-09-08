package core.actions;

import core.dispatch.EventDispatchBus;
import core.events.SeekByAmountEvent;
import core.preferences.PreferencesManager;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/** Factory for creating SeekAction instances with different configurations. */
@Singleton
public class SeekActionFactory {

    private final EventDispatchBus eventBus;
    private final PreferencesManager preferencesManager;

    @Inject
    public SeekActionFactory(EventDispatchBus eventBus, PreferencesManager preferencesManager) {
        this.eventBus = eventBus;
        this.preferencesManager = preferencesManager;
    }

    public SeekAction createSeekAction(String arg) {
        // Parse the arg string like "forward-small" or "backward-large"
        SeekByAmountEvent.Direction direction;
        SeekAction.Size size;

        if (arg.startsWith("forward")) {
            direction = SeekByAmountEvent.Direction.FORWARD;
        } else if (arg.startsWith("backward")) {
            direction = SeekByAmountEvent.Direction.BACKWARD;
        } else {
            // Default
            direction = SeekByAmountEvent.Direction.FORWARD;
        }

        if (arg.contains("large")) {
            size = SeekAction.Size.LARGE;
        } else if (arg.contains("medium")) {
            size = SeekAction.Size.MEDIUM;
        } else {
            size = SeekAction.Size.SMALL;
        }

        return new SeekAction(eventBus, preferencesManager, direction, size);
    }
}
