package actions;

import actions.ActionsFileParser.ActionConfig;
import env.PreferencesManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import shortcuts.Shortcut;

/**
 * UserDB implementation that works with ActionConfig instead of XAction.
 * This provides the same functionality as the old UserDB but for the new actions system.
 */
public class ActionConfigUserDB {
    private static final Logger logger = LoggerFactory.getLogger(ActionConfigUserDB.class);

    private final List<ActionConfig> defaultActionConfigs;
    private final ActionConfigListener listener;
    private final PreferencesManager preferencesManager;

    private static final String NO_SHORTCUT = "#";

    public interface ActionConfigListener {
        void actionConfigUpdated(ActionConfig actionConfig, Shortcut oldShortcut);
    }

    public ActionConfigUserDB(
            @NonNull PreferencesManager preferencesManager,
            @NonNull List<ActionConfig> defaultActionConfigs,
            @NonNull ActionConfigListener listener) {
        this.preferencesManager = preferencesManager;
        this.defaultActionConfigs = defaultActionConfigs;
        this.listener = listener;
    }

    public void store(ActionConfig actionConfig) {
        String key = makeId(actionConfig.className(), actionConfig.enumValue().orElse(null));
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
            Shortcut shortcut = Shortcut.fromInternalForm(storedStr);
            if (shortcut == null) {
                logger.warn("{} won't retrieve() unparseable: {}", getClass().getName(), storedStr);
                return null;
            }
            return shortcut;
        }
    }

    public void persistDefaults(boolean overwrite) {
        for (ActionConfig actionConfig : defaultActionConfigs) {
            String id = makeId(actionConfig.className(), actionConfig.enumValue().orElse(null));
            if (overwrite || retrieve(id) == null) {
                store(actionConfig);
            }
        }
    }

    public Map<String, Shortcut> retrieveAll() {
        Map<String, Shortcut> result = new HashMap<>();
        for (ActionConfig actionConfig : defaultActionConfigs) {
            String id = makeId(actionConfig.className(), actionConfig.enumValue().orElse(null));
            result.put(id, retrieve(id));
        }
        return result;
    }

    public List<ActionConfig> getDefaultActionConfigs() {
        return defaultActionConfigs;
    }

    public ActionConfig findActionConfigById(String id) {
        for (ActionConfig actionConfig : defaultActionConfigs) {
            String configId = makeId(actionConfig.className(), actionConfig.enumValue().orElse(null));
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
    public ActionConfig withShortcut(ActionConfig originalConfig, Shortcut newShortcut) {
        return new ActionConfig(
                originalConfig.className(),
                originalConfig.name(),
                originalConfig.tooltip(),
                originalConfig.enumValue(),
                newShortcut != null ? java.util.Optional.of(newShortcut) : java.util.Optional.empty()
        );
    }

    /**
     * Creates an action ID from class name and enum value.
     * This matches the ID generation logic used in ActionsManager.
     */
    private String makeId(String className, String enumValue) {
        return enumValue != null ? className + "-" + enumValue : className;
    }
}
