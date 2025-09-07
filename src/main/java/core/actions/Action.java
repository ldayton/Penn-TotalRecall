package core.actions;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Base class for all actions in the application.
 *
 * <p>Actions encapsulate executable behavior with observable state. UI frameworks can observe
 * actions to update their representations when the action's state changes.
 */
public abstract class Action {
    private final List<Runnable> observers = new CopyOnWriteArrayList<>();

    /** Execute this action's behavior. */
    public abstract void execute();

    /**
     * Whether this action is currently enabled.
     *
     * @return true if the action can be executed
     */
    public abstract boolean isEnabled();

    /**
     * The display label for this action.
     *
     * @return the label to display in UI
     */
    public abstract String getLabel();

    /**
     * The tooltip text for this action.
     *
     * @return the tooltip text, or empty string if none
     */
    public String getTooltip() {
        return "";
    }

    /**
     * The keyboard shortcut for this action.
     *
     * @return the shortcut string (e.g., "ctrl+S"), or null if none
     */
    public String getShortcut() {
        return null;
    }

    /**
     * Add an observer to be notified when this action's state changes.
     *
     * @param observer the observer to add
     */
    public final void addObserver(Runnable observer) {
        if (observer != null) {
            observers.add(observer);
            // Immediately notify to sync initial state
            observer.run();
        }
    }

    /**
     * Remove an observer.
     *
     * @param observer the observer to remove
     */
    public final void removeObserver(Runnable observer) {
        observers.remove(observer);
    }

    /**
     * Notify all observers that this action's state has changed. Subclasses should call this when
     * their state changes.
     */
    protected final void notifyObservers() {
        observers.forEach(Runnable::run);
    }
}
