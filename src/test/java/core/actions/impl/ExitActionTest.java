package core.actions.impl;

import static org.junit.jupiter.api.Assertions.*;

import annotations.Windowing;
import app.swing.SwingTestFixture;
import core.env.PreferenceKeys;
import core.preferences.PreferencesManager;
import java.awt.Container;
import java.awt.Window;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import ui.layout.MainFrame;

/**
 * Tests for ExitAction to ensure proper quit dialog behavior. Verifies that clicking "No" on the
 * quit confirmation dialog prevents exit.
 */
@Windowing
class ExitActionTest extends SwingTestFixture {

    @Test
    void clickingNoOnQuitDialogPreventsExit() throws Exception {
        // Arrange
        PreferencesManager prefsManager = getInstance(PreferencesManager.class);
        MainFrame mainFrame = getInstance(MainFrame.class);

        // Enable warn on exit
        prefsManager.putBoolean(PreferenceKeys.WARN_ON_EXIT, true);

        // Get the ExitAction instance
        ExitAction exitAction = getInstance(ExitAction.class);

        // Act: Trigger the exit action directly
        SwingUtilities.invokeLater(() -> exitAction.execute());

        // Wait for dialog to appear
        Thread.sleep(500);

        // Find the confirmation dialog
        SwingUtilities.invokeAndWait(
                () -> {
                    Window[] windows = Window.getWindows();
                    JDialog confirmDialog = null;

                    for (Window w : windows) {
                        if (w instanceof JDialog && w.isVisible()) {
                            JDialog dialog = (JDialog) w;
                            // Look for a dialog with Yes/No buttons
                            if (hasYesNoButtons(dialog)) {
                                confirmDialog = dialog;
                                break;
                            }
                        }
                    }

                    assertNotNull(confirmDialog, "Quit confirmation dialog should be shown");

                    // Find and click the "No" button
                    JButton noButton = findButtonWithText(confirmDialog, "No");
                    assertNotNull(noButton, "Dialog should have a No button");

                    // Click "No" to cancel the quit
                    noButton.doClick();
                });

        // Wait a bit to ensure any exit would have been attempted
        Thread.sleep(200);

        // Assert: Main frame should still be visible after cancelling
        assertTrue(
                mainFrame.isVisible(), "MainFrame should still be visible after cancelling quit");
    }

    private boolean hasYesNoButtons(JDialog dialog) {
        return findButtonWithText(dialog, "Yes") != null
                && findButtonWithText(dialog, "No") != null;
    }

    private JButton findButtonWithText(Container container, String text) {
        for (java.awt.Component comp : container.getComponents()) {
            if (comp instanceof JButton) {
                JButton button = (JButton) comp;
                if (text.equals(button.getText())) {
                    return button;
                }
            } else if (comp instanceof Container) {
                JButton found = findButtonWithText((Container) comp, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
