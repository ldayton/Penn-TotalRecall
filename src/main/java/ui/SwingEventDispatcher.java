package ui;

import core.dispatch.EventDispatcher;
import jakarta.inject.Singleton;
import javax.swing.SwingUtilities;

/**
 * Swing implementation of EventDispatcher that uses SwingUtilities to ensure events are delivered
 * on the Event Dispatch Thread (EDT).
 */
@Singleton
public class SwingEventDispatcher implements EventDispatcher {

    @Override
    public boolean isEventDispatchThread() {
        return SwingUtilities.isEventDispatchThread();
    }

    @Override
    public void dispatch(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) {
            // Already on EDT, execute immediately
            task.run();
        } else {
            // Queue for execution on EDT
            SwingUtilities.invokeLater(task);
        }
    }
}
