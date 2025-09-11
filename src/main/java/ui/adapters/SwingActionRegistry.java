package ui.adapters;

import core.actions.Action;
import core.actions.ActionRegistry;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import ui.actions.ActionManager;

/**
 * Registry that provides Swing adapters for core actions.
 *
 * <p>This registry creates SwingAction adapters for all core actions and registers them with the
 * ActionsManager for keyboard shortcut support.
 */
@Singleton
public class SwingActionRegistry {
    private final Map<Class<? extends Action>, SwingAction> swingAdapters = new HashMap<>();

    @Inject
    public SwingActionRegistry(ActionRegistry actionRegistry, ActionManager actionsManager) {

        // Create Swing adapters for all actions
        for (Action action : actionRegistry.getAllActions()) {
            SwingAction swingAction = new SwingAction(action);
            swingAdapters.put(action.getClass(), swingAction);

            // Register with ActionsManager for keyboard shortcuts from actions.xml
            actionsManager.registerAction(swingAction, null);
        }
    }

    public Optional<SwingAction> getSwingAction(Class<? extends Action> actionClass) {
        return Optional.ofNullable(swingAdapters.get(actionClass));
    }
}
