package core.actions;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Registry that holds all core actions in the application.
 *
 * <p>This registry is populated automatically by Guice with all Action implementations found via
 * auto-discovery. It provides a central place to access all actions without UI dependencies.
 */
@Singleton
public class ActionRegistry {
    private final Map<Class<? extends Action>, Action> actionsByClass = new HashMap<>();
    private final Set<Action> allActions;

    /**
     * Create the registry with all discovered actions.
     *
     * @param actions all actions discovered and injected by Guice
     */
    @Inject
    public ActionRegistry(Set<Action> actions) {
        this.allActions = actions;

        // Index actions by class
        for (Action action : actions) {
            actionsByClass.put(action.getClass(), action);
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
}
