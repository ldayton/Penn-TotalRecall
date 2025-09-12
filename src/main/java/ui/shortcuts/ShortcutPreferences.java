package ui.shortcuts;

import core.preferences.PreferencesManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ui.KeyboardManager;
import ui.adapters.SwingActionConfig;

/**
 * ShortcutPreferences for managing user preferences for ActionConfig objects. This provides user
 * preference storage and retrieval functionality for the actions system.
 */
public class ShortcutPreferences {
    private static final Logger logger = LoggerFactory.getLogger(ShortcutPreferences.class);

    private final List<SwingActionConfig> defaultActionConfigs;
    private final ActionConfigListener listener;
    private final PreferencesManager preferencesManager;
    private final KeyboardManager keyboardManager;

    private static final String NO_SHORTCUT = "#";

    public interface ActionConfigListener {
        void actionConfigUpdated(SwingActionConfig actionConfig, Shortcut oldShortcut);
    }

    public ShortcutPreferences(
            @NonNull PreferencesManager preferencesManager,
            @NonNull List<SwingActionConfig> defaultActionConfigs,
            @NonNull ActionConfigListener listener,
            @NonNull KeyboardManager keyboardManager) {
        this.preferencesManager = preferencesManager;
        this.defaultActionConfigs = defaultActionConfigs;
        this.listener = listener;
        this.keyboardManager = keyboardManager;
    }

    public void store(SwingActionConfig actionConfig) {
        String key = makeId(actionConfig.className(), actionConfig.arg().orElse(null));
        Shortcut oldShortcut = retrieveAll().get(key);

        String value;
        if (actionConfig.shortcut().isPresent()) {
            value = actionConfig.shortcut().get().getInternalForm();
        } else {
            value = NO_SHORTCUT;
        }

        listener.actionConfigUpdated(actionConfig, oldShortcut);
        preferencesManager.putString(key, value);
    }

    public Shortcut retrieve(String id) {
        String storedStr = preferencesManager.getString(id, NO_SHORTCUT);

        if (NO_SHORTCUT.equals(storedStr)) {
            return null;
        } else {
            Shortcut shortcut = Shortcut.fromInternalForm(storedStr, keyboardManager);
            if (shortcut == null) {
                logger.warn("{} won't retrieve() unparseable: {}", getClass().getName(), storedStr);
                return null;
            }
            return shortcut;
        }
    }

    public void persistDefaults(boolean overwrite) {
        for (SwingActionConfig actionConfig : defaultActionConfigs) {
            String id = makeId(actionConfig.className(), actionConfig.arg().orElse(null));
            if (overwrite || retrieve(id) == null) {
                store(actionConfig);
            }
        }
    }

    public Map<String, Shortcut> retrieveAll() {
        Map<String, Shortcut> result = new HashMap<>();
        for (SwingActionConfig actionConfig : defaultActionConfigs) {
            String id = makeId(actionConfig.className(), actionConfig.arg().orElse(null));
            result.put(id, retrieve(id));
        }
        return result;
    }

    public List<SwingActionConfig> getDefaultActionConfigs() {
        return defaultActionConfigs;
    }

    public SwingActionConfig findActionConfigById(String id) {
        for (SwingActionConfig actionConfig : defaultActionConfigs) {
            String configId = makeId(actionConfig.className(), actionConfig.arg().orElse(null));
            if (configId.equals(id)) {
                return actionConfig;
            }
        }
        return null;
    }

    /**
     * Creates an ActionConfig with an updated shortcut.
     *
     * @param originalConfig The original ActionConfig
     * @param newShortcut The new shortcut (can be null)
     * @return A new ActionConfig with the updated shortcut
     */
    public SwingActionConfig withShortcut(SwingActionConfig originalConfig, Shortcut newShortcut) {
        return new SwingActionConfig(
                originalConfig.className(),
                originalConfig.name(),
                originalConfig.tooltip(),
                newShortcut != null ? Optional.of(newShortcut) : Optional.empty());
    }

    /**
     * Creates an action ID from class name and argument. This matches the ID generation logic used
     * in ActionsManager.
     */
    private String makeId(String className, String arg) {
        return arg != null ? className + "-" + arg : className;
    }
}
