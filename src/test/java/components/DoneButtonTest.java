package components;

import static org.junit.jupiter.api.Assertions.*;

import annotation.MacOS;
import di.GuiceBootstrap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for DoneButton functionality. */
@MacOS
class DoneButtonTest {

    private DoneButton doneButton;

    @BeforeEach
    void setUp() {
        // Create the application bootstrap to ensure proper DI setup
        GuiceBootstrap bootstrap = GuiceBootstrap.create();

        // Start the application to initialize XActionManager and update actions
        // This is necessary for the action system to work properly
        bootstrap.startApplication();

        doneButton = GuiceBootstrap.getInjectedInstance(DoneButton.class);
    }

    @Test
    @DisplayName("DoneButton should have correct text set from action system")
    void doneButtonShouldHaveCorrectText() {
        // The button should have the text "Mark Complete" set from the action system
        String buttonText = doneButton.getText();
        assertEquals(
                "Mark Complete",
                buttonText,
                "DoneButton should display 'Mark Complete' text from the action system");
    }

    @Test
    @DisplayName("DoneButton should be disabled when no audio file is open")
    void doneButtonShouldBeDisabledWhenNoAudioOpen() {
        // When no audio file is open, the button should be disabled
        assertFalse(
                doneButton.isEnabled(), "DoneButton should be disabled when no audio file is open");
    }

    @Test
    @DisplayName("DoneButton should have proper size to display text")
    void doneButtonShouldHaveProperSize() {
        // The button should have a reasonable height to display its text
        int buttonHeight = doneButton.getPreferredSize().height;
        assertTrue(
                buttonHeight > 20,
                "DoneButton should have height > 20 pixels to properly display text, but was: "
                        + buttonHeight);
    }

    @Test
    @DisplayName("DoneButton should be properly configured after calling updateActions")
    void doneButtonShouldBeProperlyConfiguredAfterUpdateActions() {
        // Call updateActions to trigger the action system
        MyMenu.updateActions();

        // Now the button should have proper text and be disabled
        String buttonText = doneButton.getText();
        assertEquals(
                "Mark Complete",
                buttonText,
                "DoneButton should have text 'Mark Complete' from actions.xml after"
                        + " updateActions()");

        boolean isEnabled = doneButton.isEnabled();
        assertFalse(
                isEnabled,
                "DoneButton should be disabled when no audio file is open after updateActions()");
    }

    @Test
    @DisplayName("XActionManager should be initialized with actions.xml before updateActions")
    void xActionManagerShouldBeInitializedWithActionsXml() {
        // The XActionManager should be initialized with actions.xml
        // This is what ShortcutFrame.createDefault() does in the old system
        ShortcutFrame.createDefault();

        // Now call updateActions
        MyMenu.updateActions();

        // The button should now be properly configured
        String buttonText = doneButton.getText();
        assertEquals(
                "Mark Complete",
                buttonText,
                "DoneButton should have text 'Mark Complete' after XActionManager initialization");

        boolean isEnabled = doneButton.isEnabled();
        assertFalse(
                isEnabled,
                "DoneButton should be disabled when no audio file is open after XActionManager"
                        + " initialization");
    }
}
