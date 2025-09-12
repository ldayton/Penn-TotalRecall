package core.actions;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.NonNull;

/**
 * Base class for all actions in the application.
 *
 * <p>Actions encapsulate executable behavior with observable state. UI frameworks can observe
 * actions to update their representations when the action's state changes.
 */
public abstract class Action {

    private final List<Runnable> observers = new CopyOnWriteArrayList<>();
    private static ActionRegistry actionRegistry;

    public static void setActionRegistry(@NonNull ActionRegistry registry) {
        actionRegistry = registry;
    }

    public abstract void execute();

    public abstract boolean isEnabled();

    public String getLabel() {
        if (actionRegistry != null) {
            Optional<ActionConfig> config = actionRegistry.getConfig(getClass().getSimpleName());
            if (config.isPresent()) {
                return config.get().name();
            }
        }
        // Fallback to default implementation if not in registry
        return getDefaultLabel();
    }

    protected String getDefaultLabel() {
        // Subclasses can override this to provide a default
        return getClass().getSimpleName();
    }

    public Optional<String> getTooltip() {
        if (actionRegistry != null) {
            Optional<ActionConfig> config = actionRegistry.getConfig(getClass().getSimpleName());
            if (config.isPresent()) {
                return config.get().tooltip();
            }
        }
        // Fallback to default implementation if not in registry
        return getDefaultTooltip();
    }

    protected Optional<String> getDefaultTooltip() {
        // No default tooltips - only from configuration
        return Optional.empty();
    }

    public Optional<ShortcutSpec> getShortcut() {
        if (actionRegistry != null) {
            Optional<ActionConfig> config = actionRegistry.getConfig(getClass().getSimpleName());
            if (config.isPresent()) {
                return config.get().shortcut();
            }
        }
        // No default shortcuts - must be configured
        return Optional.empty();
    }

    public final void addObserver(@NonNull Runnable observer) {
        observers.add(observer);
        observer.run();
    }

    public final void removeObserver(@NonNull Runnable observer) {
        observers.remove(observer);
    }

    protected final void notifyObservers() {
        observers.forEach(Runnable::run);
    }
}
