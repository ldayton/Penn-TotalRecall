package ui.adapters;

import core.actions.Action;
import core.actions.ActionConfig;
import core.actions.ActionRegistry;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.NonNull;

/**
 * Registry that provides Swing adapters for core actions.
 *
 * <p>This registry creates and maintains a single SwingAction adapter for each core action. These
 * instances are the single source of truth for Swing representations of actions, ensuring that
 * keyboard shortcuts and other configurations are properly applied.
 */
@Singleton
public class SwingActionRegistry {
    private final Map<Class<? extends Action>, SwingAction> swingAdapters = new HashMap<>();

    @Inject
    public SwingActionRegistry(
            @NonNull ActionRegistry actionRegistry, @NonNull ShortcutConverter shortcutConverter) {

        // Initialize the action registry to load configs
        actionRegistry.initialize();

        // Create ONE SwingAction adapter for each core action
        for (Action action : actionRegistry.getAllActions()) {
            String className = action.getClass().getSimpleName();

            // Get the config for this action (may be null if no config exists)
            ActionConfig config = actionRegistry.getConfig(className).orElse(null);

            // Create the SwingAction with the config
            SwingAction swingAction = new SwingAction(action, config, shortcutConverter);
            swingAdapters.put(action.getClass(), swingAction);
        }
    }

    /**
     * Get the single SwingAction instance for a given action class.
     *
     * @param actionClass The class of the core action
     * @return The SwingAction adapter for this action
     */
    public SwingAction get(@NonNull Class<? extends Action> actionClass) {
        SwingAction swingAction = swingAdapters.get(actionClass);
        if (swingAction == null) {
            throw new IllegalArgumentException(
                    "No SwingAction registered for " + actionClass.getSimpleName());
        }
        return swingAction;
    }

    /**
     * Get the single SwingAction instance for a given action class, if it exists.
     *
     * @param actionClass The class of the core action
     * @return An Optional containing the SwingAction adapter, or empty if not found
     */
    public Optional<SwingAction> getOptional(@NonNull Class<? extends Action> actionClass) {
        return Optional.ofNullable(swingAdapters.get(actionClass));
    }

    /**
     * Get all registered SwingActions.
     *
     * @return All SwingAction adapters
     */
    public Set<SwingAction> getAllSwingActions() {
        return new HashSet<>(swingAdapters.values());
    }
}
