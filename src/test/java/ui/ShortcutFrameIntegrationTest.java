package ui;

import static org.junit.jupiter.api.Assertions.*;

import app.swing.SwingTestFixture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Integration test that opens the Shortcut editor via the real menu action. */
@DisplayName("Shortcut Editor Integration")
class ShortcutFrameIntegrationTest extends SwingTestFixture {

    @Test
    @DisplayName("launches Shortcut editor via menu action and shows window")
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void launchesShortcutEditorViaMenuAction() throws Exception {
        // Trigger EditShortcutsAction on the EDT
        onEdt(
                () -> {
                    var action = getInstance(core.actions.impl.EditShortcutsAction.class);
                    action.execute();
                });

        // Verify ShortcutFrame became visible
        boolean shown = waitForWindow(ui.ShortcutFrame.class, 5);
        assertTrue(shown, "Shortcut editor window should become visible");
    }
}
