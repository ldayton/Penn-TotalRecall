package core.events;

/** Event for requesting layout updates. This eliminates direct UI control from business logic. */
public class LayoutUpdateRequestedEvent {
    public enum Type {
        ENABLE_CONTINUOUS,
        DISABLE_CONTINUOUS
    }

    private final Type type;

    public LayoutUpdateRequestedEvent(Type type) {
        this.type = type;
    }

    public Type getType() {
        return type;
    }
}
