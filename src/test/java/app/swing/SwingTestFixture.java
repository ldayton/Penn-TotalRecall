package app.swing;

import annotations.Windowing;
import java.awt.Window;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 * Base fixture for Swing integration tests that need the DI container. Handles bootstrap and
 * cleanup of SwingApp without launching the full Main.
 */
@Windowing
@Slf4j
public abstract class SwingTestFixture {
    private static final int STARTUP_TIMEOUT_SECONDS = 10;

    protected SwingApp app;

    @BeforeEach
    void setUp() throws Exception {
        log.info("Starting SwingApp for test: {}", getClass().getSimpleName());

        // Bootstrap the application asynchronously
        CompletableFuture<SwingApp> future =
                CompletableFuture.supplyAsync(
                        () -> {
                            SwingApp bootstrap = SwingApp.create();
                            bootstrap.startApplication();
                            return bootstrap;
                        });

        // Wait for bootstrap to complete
        app = future.get(STARTUP_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Wait for GUI to be ready
        waitForSwingReady();
        log.info("SwingApp ready for test");
    }

    @AfterEach
    void tearDown() throws Exception {
        log.info("Cleaning up SwingApp after test");

        // Dispose all windows on EDT
        SwingUtilities.invokeAndWait(
                () -> {
                    for (Window window : Window.getWindows()) {
                        if (window.isDisplayable()) {
                            log.debug("Disposing window: {}", window.getClass().getSimpleName());
                            window.dispose();
                        }
                    }
                });

        // Give cleanup a moment to complete
        Thread.sleep(500);
        log.info("SwingApp cleanup complete");
    }

    /** Gets an instance from the DI container. */
    protected <T> T getInstance(Class<T> clazz) {
        return SwingApp.getInjectedInstance(clazz);
    }

    /** Executes an action on the EDT and waits for completion. */
    protected void onEdt(Runnable action) throws Exception {
        SwingUtilities.invokeAndWait(action);
    }

    /**
     * Waits for a window of the specified type to become visible.
     *
     * @param windowClass the window class to wait for
     * @param timeoutSeconds maximum time to wait
     * @return true if window appeared, false if timeout
     */
    protected boolean waitForWindow(Class<? extends Window> windowClass, int timeoutSeconds)
            throws Exception {
        long endTime = System.currentTimeMillis() + timeoutSeconds * 1000L;

        while (System.currentTimeMillis() < endTime) {
            final boolean[] found = {false};
            SwingUtilities.invokeAndWait(
                    () -> {
                        for (Window window : Window.getWindows()) {
                            if (windowClass.isInstance(window) && window.isVisible()) {
                                found[0] = true;
                                return;
                            }
                        }
                    });

            if (found[0]) {
                return true;
            }

            Thread.sleep(200);
        }

        return false;
    }

    /** Finds and returns a visible window of the specified type. */
    protected <T extends Window> T findWindow(Class<T> windowClass) throws Exception {
        final Object[] result = {null};
        SwingUtilities.invokeAndWait(
                () -> {
                    for (Window window : Window.getWindows()) {
                        if (windowClass.isInstance(window) && window.isVisible()) {
                            result[0] = window;
                            return;
                        }
                    }
                });
        return windowClass.cast(result[0]);
    }

    /** Waits for Swing to be ready (at least one window visible). */
    private void waitForSwingReady() throws Exception {
        long endTime = System.currentTimeMillis() + 5000;

        while (System.currentTimeMillis() < endTime) {
            final boolean[] ready = {false};
            SwingUtilities.invokeAndWait(
                    () -> {
                        Window[] windows = Window.getWindows();
                        ready[0] = windows.length > 0;
                    });

            if (ready[0]) {
                Thread.sleep(500); // Give it a moment to fully initialize
                return;
            }

            Thread.sleep(100);
        }
    }
}
