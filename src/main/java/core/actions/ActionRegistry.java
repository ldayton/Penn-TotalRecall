package core.actions;

import actions.ActionsManager;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import ui.swing.SwingAction;

/**
 * Registry that holds all actions in the application.
 *
 * <p>This registry is populated automatically by Guice with all Action implementations found via
 * auto-discovery. It also handles registration with the legacy ActionsManager for keyboard shortcut
 * support.
 */
@Singleton
public class ActionRegistry {
    private final Map<Class<? extends Action>, Action> actionsByClass = new HashMap<>();
    private final Map<Class<? extends Action>, SwingAction> swingAdapters = new HashMap<>();
    private final Set<Action> allActions;

    /**
     * Create the registry with all discovered actions.
     *
     * @param actions all actions discovered and injected by Guice
     * @param actionsManager the legacy actions manager for shortcut registration
     */
    @Inject
    public ActionRegistry(Set<Action> actions, ActionsManager actionsManager) {
        this.allActions = actions;

        // Index actions by class and create Swing adapters
        for (Action action : actions) {
            actionsByClass.put(action.getClass(), action);

            // Create Swing adapter
            SwingAction swingAction = new SwingAction(action);
            swingAdapters.put(action.getClass(), swingAction);

            // Register with ActionsManager for keyboard shortcuts from actions.xml
            actionsManager.registerAction(swingAction, null);
        }
    }

    /**
     * Get all registered actions.
     *
     * @return all actions in the registry
     */
    public Set<Action> getAllActions() {
        return allActions;
    }

    /**
     * Get an action by its class.
     *
     * @param actionClass the action class
     * @param <T> the action type
     * @return the action instance, or empty if not found
     */
    public <T extends Action> Optional<T> getAction(Class<T> actionClass) {
        @SuppressWarnings("unchecked")
        T action = (T) actionsByClass.get(actionClass);
        return Optional.ofNullable(action);
    }

    /**
     * Get the Swing adapter for an action class.
     *
     * @param actionClass the action class
     * @return the Swing adapter, or empty if not found
     */
    public Optional<SwingAction> getSwingAction(Class<? extends Action> actionClass) {
        return Optional.ofNullable(swingAdapters.get(actionClass));
    }
}
