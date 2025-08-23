package actions;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import javax.swing.Action;
import javax.swing.InputMap;
import javax.swing.KeyStroke;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import shortcuts.Shortcut;

/**
 * Bridge class that provides the same static interface as the old XActionManager but delegates to
 * the new ActionsManager. This allows for a gradual migration from the old system to the new one.
 *
 * <p>This class maintains backward compatibility while the migration is in progress. Once the
 * migration is complete, this bridge can be removed and all code can directly use ActionsManager.
 */
@Singleton
public class ActionsManagerBridge {
    private static final Logger logger = LoggerFactory.getLogger(ActionsManagerBridge.class);

    private static ActionsManagerBridge instance;
    private final ActionsManager actionsManager;

    @Inject
    public ActionsManagerBridge(@NonNull ActionsManager actionsManager) {
        this.actionsManager = actionsManager;
        // Note: instance is set manually via initialize() method during bootstrap
    }

    /**
     * Gets the singleton instance of ActionsManagerBridge.
     *
     * @return The singleton instance
     * @throws IllegalStateException if not initialized via DI
     */
    public static ActionsManagerBridge getInstance() {
        if (instance == null) {
            throw new IllegalStateException(
                    "ActionsManagerBridge not initialized via DI. Ensure GuiceBootstrap.create()"
                            + " was called first.");
        }
        return instance;
    }

    /**
     * Initializes the ActionsManagerBridge with an ActionsManager instance. This method is called
     * during bootstrap to ensure the bridge is available before any UpdatingAction classes are
     * instantiated.
     *
     * @param actionsManager The ActionsManager instance to use
     */
    public static void initialize(ActionsManager actionsManager) {
        if (instance == null) {
            instance = new ActionsManagerBridge(actionsManager);
        }
    }

    /**
     * Looks up a KeyStroke for an action with an enum value. Delegates to ActionsManager.lookup().
     *
     * @param action The action to look up
     * @param e The enum value (can be null)
     * @return The KeyStroke for the action, or null if not found
     */
    public static KeyStroke lookup(Action action, Enum<?> e) {
        return getInstance().actionsManager.lookup(action, e);
    }

    /**
     * Looks up a KeyStroke by action ID. Delegates to ActionsManager.lookup().
     *
     * @param id The action ID
     * @return The KeyStroke for the action, or null if not found
     */
    public static KeyStroke lookup(String id) {
        return getInstance().actionsManager.lookup(id);
    }

    /**
     * Registers an input map for an action. Delegates to ActionsManager.registerInputMap().
     *
     * @param action The action to register
     * @param e The enum value (can be null)
     * @param mapKey The key for the input map
     * @param map The input map
     */
    public static void registerInputMap(Action action, Enum<?> e, String mapKey, InputMap map) {
        getInstance().actionsManager.registerInputMap(action, e, mapKey, map);
    }

    /**
     * Registers an action for updates. Delegates to ActionsManager.registerAction().
     *
     * @param action The action to register
     * @param e The enum value (can be null)
     */
    public static void registerAction(Action action, Enum<?> e) {
        getInstance().actionsManager.registerAction(action, e);
    }

    /**
     * Registers an UpdatingAction using its stored enum value. This method is called during
     * initialization to register all actions after they've been created by DI.
     *
     * @param action The UpdatingAction to register
     */
    public static void registerUpdatingAction(behaviors.UpdatingAction action) {
        getInstance().actionsManager.registerUpdatingAction(action);
    }

    /**
     * Updates an action by ID, applying the current configuration. Delegates to
     * ActionsManager.update().
     *
     * @param id The action ID
     * @param oldShortcut The old shortcut (for cleanup)
     */
    public static void update(String id, Shortcut oldShortcut) {
        getInstance().actionsManager.update(id, oldShortcut);
    }

    /**
     * Gets the underlying ActionsManager instance.
     *
     * @return The ActionsManager instance
     */
    public ActionsManager getActionsManager() {
        return actionsManager;
    }

    /**
     * Gets all action configurations for use by shortcut management.
     *
     * @return List of all action configurations
     */
    public static List<actions.ActionsFileParser.ActionConfig> getAllActionConfigs() {
        return getInstance().actionsManager.getAllActionConfigs();
    }

    /**
     * Provides a listener equivalent to XActionManager.listener for backward compatibility. This
     * allows the ShortcutManager to work with the new system.
     */
    public static final shortcuts.XActionListener listener =
            new shortcuts.XActionListener() {
                @Override
                public void xActionUpdated(shortcuts.XAction xact, shortcuts.Shortcut oldShortcut) {
                    // Convert XAction to ActionConfig and update via ActionsManager
                    String id = xact.getId();
                    // Note: This is a simplified conversion - in a full migration,
                    // we'd want to convert XAction to ActionConfig properly
                    update(id, oldShortcut);
                }
            };
}
