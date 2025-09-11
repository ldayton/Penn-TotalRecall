package ui.preferences;

import static org.junit.jupiter.api.Assertions.*;

import app.swing.SwingTestFixture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Integration test that launches the Preferences window through the real DI + event system. */
@DisplayName("Preferences Integration")
class PreferencesIntegrationTest extends SwingTestFixture {

    @Test
    @DisplayName("launches Preferences via DI on EDT and shows window")
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void launchesPreferencesViaEventBus() throws Exception {
        // Trigger Preferences via the real menu action on the EDT
        onEdt(
                () -> {
                    var prefsAction = getInstance(core.actions.PreferencesAction.class);
                    prefsAction.execute();
                });

        // Verify PreferencesFrame became visible
        boolean shown = waitForWindow(ui.preferences.PreferencesFrame.class, 5);
        assertTrue(shown, "Preferences window should become visible");
    }
}
