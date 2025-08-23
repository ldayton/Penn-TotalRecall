package control;

/**
 * Event for requesting focus changes between UI components. This eliminates direct UI control from
 * event handlers.
 */
public class FocusRequestedEvent {
    public enum Component {
        WORDPOOL_TEXT_FIELD,
        WORDPOOL_LIST,
        MAIN_WINDOW
    }

    private final Component component;

    public FocusRequestedEvent(Component component) {
        this.component = component;
    }

    public Component getComponent() {
        return component;
    }
}
