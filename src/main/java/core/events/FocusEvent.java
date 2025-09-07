package core.events;

import lombok.NonNull;

/**
 * Event for requesting focus changes between interface components. This eliminates direct component
 * control from event handlers.
 */
public record FocusEvent(@NonNull Component component) {
    public enum Component {
        WORDPOOL_TEXT_FIELD,
        WORDPOOL_LIST,
        MAIN_WINDOW
    }
}
