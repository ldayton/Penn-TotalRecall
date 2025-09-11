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

    public abstract void execute();

    public abstract boolean isEnabled();

    public abstract String getLabel();

    public Optional<String> getTooltip() {
        return Optional.empty();
    }

    public Optional<ShortcutSpec> getShortcut() {
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
