package actions;

import actions.ActionsFileParser.ActionConfig;
import components.shortcuts.Shortcut;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.swing.Action;
import javax.swing.InputMap;
import javax.swing.KeyStroke;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages action configuration loading from actions.xml and provides the same interface as the old
 * XActionManager but using the new ActionsFileParser.
 *
 * <p>This class bridges the new parser with the existing behavior system, allowing for a gradual
 * migration from the old XActionManager to this new system.
 */
@Singleton
public class ActionsManager {
    private static final Logger logger = LoggerFactory.getLogger(ActionsManager.class);

    private final ActionsFileParser actionsFileParser;
    private final Map<String, ActionConfig> actionConfigs = new HashMap<>();
    private final Map<String, Set<Action>> listenersMap = new HashMap<>();
    private final Map<String, List<InputMapPair>> inputMapMap = new HashMap<>();

    @Inject
    public ActionsManager(@NonNull ActionsFileParser actionsFileParser) {
        this.actionsFileParser = actionsFileParser;
    }

    /**
     * Initializes action configuration by loading actions.xml and applying properties to actions.
     * Should be called during application startup before any UI components are created.
     */
    public void initialize() {
        try {
            // Load actions.xml from classpath (same as old system)
            var actionConfigs = actionsFileParser.parseActionsFromClasspath("/actions.xml");

            // Store action configs by ID for lookup
            for (var config : actionConfigs) {
                var id = makeId(config.className(), config.enumValue().orElse(null));
                this.actionConfigs.put(id, config);
            }

            logger.info("Loaded {} action configurations from actions.xml", actionConfigs.size());
        } catch (ActionsFileParser.ActionParseException e) {
            logger.error("Failed to initialize ActionsManager", e);
            throw new RuntimeException("Failed to initialize ActionsManager", e);
        }
    }

    /**
     * Looks up a KeyStroke for an action with an enum value.
     *
     * @param action The action to look up
     * @param e The enum value (can be null)
     * @return The KeyStroke for the action, or null if not found
     */
    public KeyStroke lookup(Action action, Enum<?> e) {
        var id = makeId(action.getClass().getName(), e != null ? e.name() : null);
        return lookup(id);
    }

    /**
     * Looks up a KeyStroke by action ID.
     *
     * @param id The action ID
     * @return The KeyStroke for the action, or null if not found
     */
    public KeyStroke lookup(String id) {
        var config = actionConfigs.get(id);
        if (config != null && config.shortcut().isPresent()) {
            return config.shortcut().get().stroke;
        }
        return null;
    }

    /**
     * Registers an input map for an action.
     *
     * @param action The action to register
     * @param e The enum value (can be null)
     * @param mapKey The key for the input map
     * @param map The input map
     */
    public void registerInputMap(Action action, Enum<?> e, String mapKey, InputMap map) {
        var id = makeId(action.getClass().getName(), e != null ? e.name() : null);
        inputMapMap.computeIfAbsent(id, k -> new ArrayList<>()).add(new InputMapPair(mapKey, map));
    }

    /**
     * Registers an action for updates.
     *
     * @param action The action to register
     * @param e The enum value (can be null)
     */
    public void registerAction(Action action, Enum<?> e) {
        var id = makeId(action.getClass().getName(), e != null ? e.name() : null);
        listenersMap.computeIfAbsent(id, k -> new HashSet<>()).add(action);
        update(id, null);
    }

    /**
     * Registers an UpdatingAction using its stored enum value. This method is called during
     * initialization to register all actions after they've been created by DI.
     *
     * @param action The UpdatingAction to register
     */
    public void registerUpdatingAction(behaviors.UpdatingAction action) {
        registerAction(action, action.getActionEnum());
    }

    /**
     * Updates an action by ID, applying the current configuration.
     *
     * @param id The action ID
     * @param oldShortcut The old shortcut (for cleanup)
     */
    public void update(String id, Shortcut oldShortcut) {
        var actions = listenersMap.get(id);
        var config = actionConfigs.get(id);

        if (config != null && actions != null) {
            var stroke = config.shortcut().map(s -> s.stroke).orElse(null);

            // Update all registered actions
            for (var action : actions) {
                action.putValue(Action.NAME, config.name());
                action.putValue(Action.SHORT_DESCRIPTION, config.tooltip().orElse(null));
                action.putValue(Action.ACCELERATOR_KEY, stroke);
            }

            // Update input maps
            var inputMapPairs = inputMapMap.get(id);
            if (inputMapPairs != null) {
                for (var pair : inputMapPairs) {
                    var inputMap = pair.inputMap();
                    var oldStroke = oldShortcut != null ? oldShortcut.stroke : null;
                    var inputMapKey = pair.mapKey();

                    if (oldStroke != null) {
                        inputMap.remove(oldStroke);
                    }
                    if (stroke != null) {
                        inputMap.put(stroke, inputMapKey);
                    }
                }
            }
        }
    }

    /**
     * Gets an action configuration by ID.
     *
     * @param id The action ID
     * @return The action configuration, or empty if not found
     */
    public Optional<ActionConfig> getActionConfig(String id) {
        return Optional.ofNullable(actionConfigs.get(id));
    }

    /**
     * Gets all action configurations.
     *
     * @return List of all action configurations
     */
    public List<ActionConfig> getAllActionConfigs() {
        return new ArrayList<>(actionConfigs.values());
    }

    /**
     * Creates an action ID from class name and enum value.
     *
     * @param className The class name
     * @param enumValue The enum value (can be null)
     * @return The action ID
     */
    private static String makeId(String className, String enumValue) {
        return enumValue != null ? className + "-" + enumValue : className;
    }

    /** Pair class for input map registration. */
    private record InputMapPair(String mapKey, InputMap inputMap) {}
}
