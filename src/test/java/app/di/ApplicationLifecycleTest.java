package app.di;

import static org.junit.jupiter.api.Assertions.*;

import annotation.Windowing;
import app.Main;
import java.awt.Window;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration tests for complete application lifecycle.
 *
 * <p>These tests actually start the full application (including GUI) and verify it can start and
 * shut down cleanly. This catches issues that pure dependency injection tests miss, such as:
 *
 * <ul>
 *   <li>GUI initialization problems
 *   <li>Threading issues in Swing EDT
 *   <li>Resource loading failures
 *   <li>Audio system initialization in real environment
 *   <li>Application shutdown and cleanup
 * </ul>
 *
 * <p>These tests require a headed environment (display) and are automatically skipped in CI.
 */
@DisplayName("Application Lifecycle Integration")
@Windowing
class ApplicationLifecycleTest {
    private static final Logger logger = LoggerFactory.getLogger(ApplicationLifecycleTest.class);

    // Generous timeouts for application startup/shutdown
    private static final int STARTUP_TIMEOUT_SECONDS = 30;
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 10;

    @Test
    @DisplayName("application can start and shut down cleanly")
    @Timeout(value = 45, unit = TimeUnit.SECONDS)
    void applicationCanStartAndShutDownCleanly() throws Exception {
        logger.info("🚀 Testing full application startup and shutdown...");

        // Start application in background thread
        CompletableFuture.runAsync(
                () -> {
                    try {
                        logger.info("Starting main application...");
                        // This is the actual application entry point
                        Main.main(new String[0]);
                    } catch (Exception e) {
                        logger.error("Application startup failed", e);
                        throw new RuntimeException("Application startup failed", e);
                    }
                });

        // Wait for application to start (check that GUI appears)
        boolean guiStarted = waitForGuiToAppear(STARTUP_TIMEOUT_SECONDS);
        assertTrue(
                guiStarted,
                "Application GUI should appear within " + STARTUP_TIMEOUT_SECONDS + " seconds");
        logger.info("✅ Application GUI started successfully");

        // Verify application is in expected state
        SwingUtilities.invokeAndWait(
                () -> {
                    Window[] windows = Window.getWindows();
                    assertTrue(windows.length > 0, "At least one window should be open");

                    // Find the main application window
                    boolean foundMainWindow = false;
                    for (Window window : windows) {
                        if (window.isDisplayable() && window.isVisible()) {
                            foundMainWindow = true;
                            logger.info(
                                    "✅ Found visible main window: "
                                            + window.getClass().getSimpleName());
                            break;
                        }
                    }
                    assertTrue(foundMainWindow, "Should have at least one visible window");
                });

        // Give the application a moment to fully initialize
        Thread.sleep(2000);
        logger.info("✅ Application appears to be running normally");

        // Shut down the application cleanly
        logger.info("Initiating application shutdown...");
        CompletableFuture<Void> shutdownFuture =
                CompletableFuture.runAsync(
                        () -> {
                            SwingUtilities.invokeLater(
                                    () -> {
                                        // Close all windows to trigger shutdown
                                        for (Window window : Window.getWindows()) {
                                            if (window.isDisplayable()) {
                                                logger.debug(
                                                        "Closing window: "
                                                                + window.getClass()
                                                                        .getSimpleName());
                                                window.dispose();
                                            }
                                        }
                                    });
                        });

        // Wait for clean shutdown
        try {
            shutdownFuture.get(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            logger.info("✅ Application shutdown completed");
        } catch (TimeoutException e) {
            fail("Application did not shut down within " + SHUTDOWN_TIMEOUT_SECONDS + " seconds");
        }

        // Verify no windows remain open
        SwingUtilities.invokeAndWait(
                () -> {
                    Window[] remainingWindows = Window.getWindows();
                    for (Window window : remainingWindows) {
                        if (window.isDisplayable() && window.isVisible()) {
                            fail(
                                    "Window still open after shutdown: "
                                            + window.getClass().getSimpleName());
                        }
                    }
                });

        logger.info("🎉 Application lifecycle test completed successfully");
    }

    /**
     * Waits for the GUI to appear by checking for visible windows.
     *
     * @param timeoutSeconds maximum time to wait for GUI
     * @return true if GUI appeared, false if timeout
     */
    private boolean waitForGuiToAppear(int timeoutSeconds) throws Exception {
        long startTime = System.currentTimeMillis();
        long timeoutMs = timeoutSeconds * 1000L;

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            // Check if any windows are visible
            final boolean[] guiFound = {false};
            SwingUtilities.invokeAndWait(
                    () -> {
                        for (Window window : Window.getWindows()) {
                            if (window.isDisplayable() && window.isVisible()) {
                                guiFound[0] = true;
                                return;
                            }
                        }
                    });

            if (guiFound[0]) {
                return true;
            }

            // Wait a bit before checking again
            Thread.sleep(500);
        }

        return false;
    }

    @Test
    @DisplayName("application handles invalid command line arguments gracefully")
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @Windowing
    void applicationHandlesInvalidCommandLineArgumentsGracefully() throws Exception {
        logger.info("🧪 Testing application with invalid command line arguments...");

        // Test with some invalid arguments
        CompletableFuture.runAsync(
                () -> {
                    try {
                        // This should not crash the application
                        Main.main(new String[] {"--invalid-flag", "--nonsense=value"});
                    } catch (Exception e) {
                        logger.error("Application failed with invalid args", e);
                        throw new RuntimeException(
                                "Application should handle invalid args gracefully", e);
                    }
                });

        // The app should still start (even with invalid args)
        boolean guiStarted = waitForGuiToAppear(15);
        assertTrue(guiStarted, "Application should start even with invalid command line arguments");
        logger.info("✅ Application handles invalid arguments gracefully");

        // Clean shutdown
        SwingUtilities.invokeLater(
                () -> {
                    for (Window window : Window.getWindows()) {
                        if (window.isDisplayable()) {
                            window.dispose();
                        }
                    }
                });

        Thread.sleep(1000); // Brief wait for cleanup
        logger.info("✅ Invalid arguments test completed");
    }
}
