package ui;

import core.actions.Action;
import core.actions.ActionRegistry;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import ui.actions.ActionsManager;
import ui.swing.SwingAction;

/**
 * Registry that provides Swing adapters for core actions.
 *
 * <p>This registry creates SwingAction adapters for all core actions and registers them with the
 * ActionsManager for keyboard shortcut support.
 */
@Singleton
public class SwingActionRegistry {
    private final Map<Class<? extends Action>, SwingAction> swingAdapters = new HashMap<>();
    private final ActionRegistry actionRegistry;

    /**
     * Create the Swing registry and register all actions with ActionsManager.
     *
     * @param actionRegistry the core action registry
     * @param actionsManager the legacy actions manager for shortcut registration
     */
    @Inject
    public SwingActionRegistry(ActionRegistry actionRegistry, ActionsManager actionsManager) {
        this.actionRegistry = actionRegistry;

        // Create Swing adapters for all actions
        for (Action action : actionRegistry.getAllActions()) {
            SwingAction swingAction = new SwingAction(action);
            swingAdapters.put(action.getClass(), swingAction);

            // Register with ActionsManager for keyboard shortcuts from actions.xml
            actionsManager.registerAction(swingAction, null);
        }
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
