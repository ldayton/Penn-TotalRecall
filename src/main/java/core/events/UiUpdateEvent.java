package core.events;

import lombok.NonNull;

/**
 * Event for requesting UI updates. This eliminates direct UI control from business logic actions.
 */
public record UiUpdateEvent(@NonNull Component component) {
    public enum Component {
        WAVEFORM_DISPLAY,
        ANNOTATION_DISPLAY,
        AUDIO_FILE_DISPLAY
    }
}
