package core.events;

/**
 * Event for requesting UI updates. This eliminates direct UI control from business logic actions.
 */
public class UIUpdateRequestedEvent {
    public enum Component {
        WAVEFORM_DISPLAY,
        ANNOTATION_DISPLAY,
        AUDIO_FILE_DISPLAY
    }

    private final Component component;

    public UIUpdateRequestedEvent(Component component) {
        this.component = component;
    }

    public Component getComponent() {
        return component;
    }
}
