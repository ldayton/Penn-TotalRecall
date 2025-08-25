package events;

import components.preferences.AbstractPreferenceDisplay;
import java.util.List;

/**
 * Event for preferences-related operations. This eliminates direct PreferencesFrame control from
 * actions.
 */
public class PreferencesEvent {
    public enum Type {
        SAVE_PREFERENCES,
        RESTORE_DEFAULTS,
        CLOSE_WINDOW,
        BRING_TO_FRONT
    }

    private final Type type;
    private final List<AbstractPreferenceDisplay> preferences;

    public PreferencesEvent(Type type) {
        this(type, null);
    }

    public PreferencesEvent(Type type, List<AbstractPreferenceDisplay> preferences) {
        this.type = type;
        this.preferences = preferences;
    }

    public Type getType() {
        return type;
    }

    public List<AbstractPreferenceDisplay> getPreferences() {
        return preferences;
    }
}
