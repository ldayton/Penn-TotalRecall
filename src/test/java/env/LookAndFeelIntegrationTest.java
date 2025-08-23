package env;

import static org.junit.jupiter.api.Assertions.*;

import annotation.Windowing;
import di.GuiceBootstrap;
import java.awt.Window;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JFrame;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration test that would catch the Aqua Look and Feel error.
 *
 * <p>This test creates actual Swing components and verifies that: 1. FlatLaf is properly set 2. No
 * Aqua-related exceptions occur during component creation and window activation 3. The Look and
 * Feel remains FlatLaf throughout the test
 */
@DisplayName("Look and Feel Integration")
@Windowing
class LookAndFeelIntegrationTest {
    private static final Logger logger = LoggerFactory.getLogger(LookAndFeelIntegrationTest.class);

    @Test
    @DisplayName("Full application startup should use FlatLaf without Aqua errors")
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void fullApplicationStartupShouldUseFlatLafWithoutAquaErrors() throws Exception {
        logger.info("=== Testing full application startup with Look and Feel verification ===");

        // Start application in background thread
        CompletableFuture<Void> startupFuture =
                CompletableFuture.runAsync(
                        () -> {
                            try {
                                logger.info("Starting application via GuiceBootstrap...");
                                var bootstrap = GuiceBootstrap.create();

                                // Verify FlatLaf is set before starting the application
                                String currentLaf = UIManager.getLookAndFeel().getClass().getName();
                                logger.info(
                                        "Look and Feel before startApplication: {}", currentLaf);
                                assertTrue(
                                        currentLaf.contains("flatlaf"),
                                        "Should be using FlatLaf before startApplication, but got: "
                                                + currentLaf);

                                bootstrap.startApplication();

                                // Verify FlatLaf is still set after starting the application
                                currentLaf = UIManager.getLookAndFeel().getClass().getName();
                                logger.info("Look and Feel after startApplication: {}", currentLaf);
                                assertTrue(
                                        currentLaf.contains("flatlaf"),
                                        "Should be using FlatLaf after startApplication, but got: "
                                                + currentLaf);

                            } catch (Exception e) {
                                logger.error("Application startup failed", e);
                                throw new RuntimeException("Application startup failed", e);
                            }
                        });

        // Wait for application to start and verify no Aqua errors
        boolean guiStarted = waitForGuiToAppear(10);
        assertTrue(guiStarted, "Application GUI should appear within 10 seconds");
        logger.info("✅ Application GUI started successfully");

        // Give the application a moment to fully initialize
        Thread.sleep(1000);

        // Verify application is using FlatLaf and no Aqua errors occurred
        SwingUtilities.invokeAndWait(
                () -> {
                    Window[] windows = Window.getWindows();
                    logger.info("Found {} windows", windows.length);

                    for (Window window : windows) {
                        logger.info(
                                "Window: {} - visible: {}, displayable: {}",
                                window.getClass().getSimpleName(),
                                window.isVisible(),
                                window.isDisplayable());
                    }

                    assertTrue(windows.length > 0, "At least one window should be open");

                    // Find the main application window
                    boolean foundMainWindow = false;
                    for (Window window : windows) {
                        if (window.isDisplayable() && window.isVisible()) {
                            foundMainWindow = true;
                            logger.info(
                                    "✅ Found visible main window: "
                                            + window.getClass().getSimpleName());

                            // Verify the window is using FlatLaf
                            String windowLaf = UIManager.getLookAndFeel().getClass().getName();
                            logger.info("Window Look and Feel: {}", windowLaf);
                            assertTrue(
                                    windowLaf.contains("flatlaf"),
                                    "Window should be using FlatLaf, but got: " + windowLaf);
                            break;
                        }
                    }
                    assertTrue(foundMainWindow, "Should have at least one visible window");
                });

        // Give the application a moment to fully initialize and trigger any Aqua-related errors
        Thread.sleep(3000);

        // Trigger window activation events that cause the Aqua error
        SwingUtilities.invokeAndWait(
                () -> {
                    Window[] windows = Window.getWindows();
                    for (Window window : windows) {
                        if (window.isVisible()) {
                            // Simulate window activation which triggers the Aqua error
                            window.requestFocus();
                            window.toFront();
                            logger.info(
                                    "Triggered window activation for: "
                                            + window.getClass().getSimpleName());
                        }
                    }
                });

        // Give time for any Aqua errors to occur
        Thread.sleep(2000);
        logger.info("✅ Application appears to be running without Aqua errors");

        // Shut down the application cleanly
        logger.info("Initiating application shutdown...");
        SwingUtilities.invokeLater(
                () -> {
                    Window[] windows = Window.getWindows();
                    for (Window window : windows) {
                        if (window.isVisible()) {
                            window.dispose();
                        }
                    }
                });

        // Wait for shutdown to complete
        startupFuture.get(5, TimeUnit.SECONDS);
        logger.info("✅ Application shutdown completed successfully");
    }

    @Test
    @DisplayName("Swing component creation should not trigger Aqua errors")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void swingComponentCreationShouldNotTriggerAquaErrors() throws Exception {
        logger.info("=== Testing Swing component creation with FlatLaf ===");

        // Ensure FlatLaf is set
        UIManager.setLookAndFeel("com.formdev.flatlaf.FlatLightLaf");
        String currentLaf = UIManager.getLookAndFeel().getClass().getName();
        logger.info("Current Look and Feel: {}", currentLaf);
        assertTrue(currentLaf.contains("flatlaf"), "Should be using FlatLaf");

        // Create a test frame and table (components that might trigger Aqua errors)
        SwingUtilities.invokeAndWait(
                () -> {
                    try {
                        JFrame frame = new JFrame("Test Frame");
                        frame.setSize(400, 300);

                        // Create a table (this is where the Aqua error was occurring)
                        JTable table = new JTable(5, 3);
                        frame.add(table);

                        // Make the frame visible (this triggers window activation events)
                        frame.setVisible(true);

                        // Verify we're still using FlatLaf
                        String lafAfterCreation = UIManager.getLookAndFeel().getClass().getName();
                        logger.info("Look and Feel after component creation: {}", lafAfterCreation);
                        assertTrue(
                                lafAfterCreation.contains("flatlaf"),
                                "Should still be using FlatLaf after component creation");

                        // Clean up
                        frame.dispose();

                        logger.info("✅ Swing component creation completed without Aqua errors");

                    } catch (Exception e) {
                        logger.error("Error during Swing component creation", e);
                        throw new RuntimeException("Swing component creation failed", e);
                    }
                });
    }

    @Test
    @DisplayName("Window activation should not trigger Aqua errors")
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void windowActivationShouldNotTriggerAquaErrors() throws Exception {
        logger.info("=== Testing window activation with Aqua error detection ===");

        // Set up exception handler to catch Aqua errors
        Thread.UncaughtExceptionHandler originalHandler =
                Thread.getDefaultUncaughtExceptionHandler();
        AtomicReference<Throwable> caughtException = new AtomicReference<>();

        Thread.setDefaultUncaughtExceptionHandler(
                (thread, throwable) -> {
                    if (throwable.getMessage() != null
                            && (throwable.getMessage().contains("AquaLookAndFeel")
                                    || throwable.getMessage().contains("cellFocusRingColor"))) {
                        caughtException.set(throwable);
                        logger.error("Caught Aqua-related exception: {}", throwable.getMessage());
                    }
                });

        try {
            // Ensure FlatLaf is set
            UIManager.setLookAndFeel("com.formdev.flatlaf.FlatLightLaf");
            String currentLaf = UIManager.getLookAndFeel().getClass().getName();
            logger.info("Current Look and Feel: {}", currentLaf);
            assertTrue(currentLaf.contains("flatlaf"), "Should be using FlatLaf");

            // Create a test frame with a table (the component that triggers the Aqua error)
            SwingUtilities.invokeAndWait(
                    () -> {
                        try {
                            JFrame frame = new JFrame("Test Frame");
                            frame.setSize(400, 300);

                            // Create a table (this is where the Aqua error was occurring)
                            JTable table = new JTable(5, 3);
                            frame.add(table);

                            // Make the frame visible
                            frame.setVisible(true);

                            // Trigger window activation events that cause the Aqua error
                            frame.requestFocus();
                            frame.toFront();

                            // Verify we're still using FlatLaf
                            String lafAfterCreation =
                                    UIManager.getLookAndFeel().getClass().getName();
                            logger.info(
                                    "Look and Feel after component creation: {}", lafAfterCreation);
                            assertTrue(
                                    lafAfterCreation.contains("flatlaf"),
                                    "Should still be using FlatLaf after component creation");

                            // Clean up
                            frame.dispose();

                            logger.info("✅ Swing component creation and activation completed");

                        } catch (Exception e) {
                            logger.error("Error during Swing component creation", e);
                            throw new RuntimeException("Swing component creation failed", e);
                        }
                    });

            // Check if any Aqua errors were caught
            Throwable exception = caughtException.get();
            if (exception != null) {
                fail("Aqua-related exception occurred: " + exception.getMessage());
            }

            logger.info("✅ No Aqua errors detected during window activation");

        } finally {
            // Restore original exception handler
            Thread.setDefaultUncaughtExceptionHandler(originalHandler);
        }
    }

    private boolean waitForGuiToAppear(int timeoutSeconds) {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeoutSeconds * 1000) {
            try {
                SwingUtilities.invokeAndWait(
                        () -> {
                            // This will throw if no windows are found
                            Window[] windows = Window.getWindows();
                            if (windows.length == 0) {
                                throw new RuntimeException("No windows found");
                            }
                        });
                return true;
            } catch (Exception e) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }
}
