package core.dispatch;

/**
 * Interface for dispatching events to the appropriate thread.
 *
 * <p>Different UI frameworks (Swing, JavaFX) or headless applications can provide their own
 * implementations to ensure events are delivered on the correct thread.
 */
public interface EventDispatcher {
    /**
     * Check if currently executing on the event dispatch thread.
     *
     * @return true if on the event dispatch thread
     */
    boolean isEventDispatchThread();

    /**
     * Execute a task on the event dispatch thread. If already on the EDT, executes immediately.
     * Otherwise, queues the task for later execution.
     *
     * @param task the task to execute
     */
    void dispatch(Runnable task);
}
