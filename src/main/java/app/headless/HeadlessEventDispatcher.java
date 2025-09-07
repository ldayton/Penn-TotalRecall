package app.headless;

import core.dispatch.EventDispatcher;
import jakarta.inject.Singleton;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Headless implementation of EventDispatcher that uses a single-threaded executor instead of
 * Swing's Event Dispatch Thread.
 */
@Singleton
public class HeadlessEventDispatcher implements EventDispatcher {

    private final ExecutorService executor;
    private volatile Thread dispatchThread;

    public HeadlessEventDispatcher() {
        // Create a single-threaded executor to act as the event dispatch thread
        this.executor =
                Executors.newSingleThreadExecutor(
                        r -> {
                            Thread t = new Thread(r, "Headless-Event-Dispatch-Thread");
                            t.setDaemon(true);
                            return t;
                        });

        // Initialize the dispatch thread reference
        executor.submit(
                () -> {
                    this.dispatchThread = Thread.currentThread();
                });
    }

    @Override
    public boolean isEventDispatchThread() {
        return Thread.currentThread() == dispatchThread;
    }

    @Override
    public void dispatch(Runnable task) {
        if (isEventDispatchThread()) {
            task.run();
        } else {
            executor.submit(task);
        }
    }

    /** Shutdown the executor service. */
    public void shutdown() {
        executor.shutdown();
    }
}
