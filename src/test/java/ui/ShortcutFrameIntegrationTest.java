package ui;

import static org.junit.jupiter.api.Assertions.*;

import annotations.Windowing;
import app.di.GuiceBootstrap;
import java.awt.Window;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Integration test that opens the Shortcut editor via the real menu action. */
@Windowing
@DisplayName("Shortcut Editor Integration")
class ShortcutFrameIntegrationTest {

    @Test
    @DisplayName("launches Shortcut editor via menu action and shows window")
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void launchesShortcutEditorViaMenuAction() throws Exception {
        // Start the application (DI + UI) asynchronously
        CompletableFuture.runAsync(
                        () -> {
                            var bootstrap = GuiceBootstrap.create();
                            bootstrap.startApplication();
                        })
                .get(10, TimeUnit.SECONDS);

        // Trigger EditShortcutsAction on the EDT
        SwingUtilities.invokeAndWait(
                () -> {
                    var action =
                            GuiceBootstrap.getInjectedInstance(actions.EditShortcutsAction.class);
                    action.actionPerformed(new java.awt.event.ActionEvent(this, 0, "shortcuts"));
                });

        // Verify ShortcutFrame became visible
        boolean shown = waitForShortcutToShow(5);
        assertTrue(shown, "Shortcut editor window should become visible");

        // Cleanup: close the ShortcutFrame
        SwingUtilities.invokeAndWait(
                () -> {
                    for (Window w : Window.getWindows()) {
                        if (w instanceof ui.ShortcutFrame && w.isDisplayable()) {
                            w.dispose();
                        }
                    }
                });
    }

    private boolean waitForShortcutToShow(int timeoutSeconds) throws Exception {
        long end = System.currentTimeMillis() + timeoutSeconds * 1000L;
        while (System.currentTimeMillis() < end) {
            final boolean[] visible = {false};
            SwingUtilities.invokeAndWait(
                    () -> {
                        for (Window w : Window.getWindows()) {
                            if (w instanceof ui.ShortcutFrame && w.isVisible()) {
                                visible[0] = true;
                                return;
                            }
                        }
                    });
            if (visible[0]) return true;
            Thread.sleep(200);
        }
        return false;
    }
}
