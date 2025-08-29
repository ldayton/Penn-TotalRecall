package ui.preferences;

import static org.junit.jupiter.api.Assertions.*;

import annotation.Windowing;
import app.di.GuiceBootstrap;
import java.awt.Window;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Integration test that launches the Preferences window through the real DI + event system. */
@Windowing
@DisplayName("Preferences Integration")
class PreferencesIntegrationTest {

    @Test
    @DisplayName("launches Preferences via DI on EDT and shows window")
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void launchesPreferencesViaEventBus() throws Exception {
        // Start the application (DI + UI) asynchronously
        CompletableFuture.runAsync(
                        () -> {
                            var bootstrap = GuiceBootstrap.create();
                            bootstrap.startApplication();
                        })
                .get(10, TimeUnit.SECONDS);

        // Trigger Preferences via the real menu action on the EDT
        SwingUtilities.invokeAndWait(
                () -> {
                    var prefsAction =
                            GuiceBootstrap.getInjectedInstance(actions.PreferencesAction.class);
                    prefsAction.actionPerformed(new java.awt.event.ActionEvent(this, 0, "prefs"));
                });

        // Verify PreferencesFrame became visible
        boolean shown = waitForPreferencesToShow(5);
        assertTrue(shown, "Preferences window should become visible");

        // Cleanup: close any visible Preferences windows
        SwingUtilities.invokeAndWait(
                () -> {
                    for (Window w : Window.getWindows()) {
                        if (w instanceof ui.preferences.PreferencesFrame && w.isDisplayable()) {
                            w.dispose();
                        }
                    }
                });
    }

    private boolean waitForPreferencesToShow(int timeoutSeconds) throws Exception {
        long end = System.currentTimeMillis() + timeoutSeconds * 1000L;
        while (System.currentTimeMillis() < end) {
            final boolean[] visible = {false};
            SwingUtilities.invokeAndWait(
                    () -> {
                        for (Window w : Window.getWindows()) {
                            if (w instanceof ui.preferences.PreferencesFrame && w.isVisible()) {
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
