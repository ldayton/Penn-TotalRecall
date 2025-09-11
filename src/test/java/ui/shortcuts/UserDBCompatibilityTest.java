package ui.shortcuts;

import static org.junit.jupiter.api.Assertions.*;

import core.env.Platform;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ui.KeyboardManager;

/** Tests to ensure existing user preferences stored in UserDB continue to work. */
class UserDBCompatibilityTest {

    private KeyboardManager keyboardManager;

    @BeforeEach
    void setUp() {
        // Create a keyboard manager for testing (using current platform)
        Platform currentPlatform = new Platform();
        keyboardManager = new KeyboardManager(currentPlatform);
    }

    @Test
    @DisplayName("Existing internal formats from user preferences should parse correctly")
    void testExistingInternalFormatsWork() {
        // Test common internal formats that could be in user preferences
        assertDoesNotThrow(
                () -> {
                    // Mac-style shortcuts (meta = Command)
                    Shortcut macCommand = createShortcut("meta shift D");
                    assertNotNull(macCommand);

                    Shortcut macControl = createShortcut("ctrl S");
                    assertNotNull(macControl);

                    // PC-style shortcuts (ctrl = Control)
                    Shortcut pcControl = createShortcut("ctrl shift D");
                    assertNotNull(pcControl);

                    Shortcut pcAlt = createShortcut("alt LEFT");
                    assertNotNull(pcAlt);

                    // Common modifier combinations
                    Shortcut shiftOnly = createShortcut("shift RIGHT");
                    assertNotNull(shiftOnly);

                    Shortcut noModifier = createShortcut("F1");
                    assertNotNull(noModifier);

                    // Special keys
                    Shortcut delete = createShortcut("meta DELETE");
                    assertNotNull(delete);

                    Shortcut enter = createShortcut("meta shift ENTER");
                    assertNotNull(enter);
                });
    }

    private Shortcut createShortcut(String internalForm) {
        KeyStroke stroke = KeyStroke.getKeyStroke(internalForm);
        if (stroke == null) {
            throw new RuntimeException("Cannot parse keystroke: " + internalForm);
        }
        return Shortcut.forPlatform(stroke, keyboardManager);
    }

    @Test
    @DisplayName("Round-trip compatibility: store() → retrieve() should preserve shortcuts")
    void testRoundTripCompatibility() {
        // Test various shortcuts that users might have customized
        String[] testInternalForms = {
            "meta shift D", // Mac Command+Shift+D
            "ctrl shift D", // PC Ctrl+Shift+D
            "alt LEFT", // Alt+Left Arrow
            "shift RIGHT", // Shift+Right Arrow
            "meta DELETE", // Command/Ctrl+Delete
            "F1", // Function key only
            "meta S", // Command/Ctrl+S
            "shift SPACE" // Shift+Space
        };

        for (String internalForm : testInternalForms) {
            // Simulate UserDB.retrieve() → store() → retrieve() cycle
            Shortcut original = createShortcut(internalForm);

            // Simulate what UserDB.store() saves
            String storedForm = original.getInternalForm();

            // Simulate what UserDB.retrieve() loads
            Shortcut restored = createShortcut(storedForm);

            assertEquals(
                    original, restored, "Round-trip failed for internal form: " + internalForm);
        }
    }

    @Test
    @DisplayName("Cross-platform modifier equivalents should work")
    void testCrossPlatformModifiers() {
        // Verify both Mac and PC style modifiers work
        assertDoesNotThrow(
                () -> {
                    // These represent the same logical shortcut on different platforms
                    Shortcut macStyle = createShortcut("meta S"); // Command+S on Mac
                    Shortcut pcStyle = createShortcut("ctrl S"); // Ctrl+S on PC

                    assertNotNull(macStyle);
                    assertNotNull(pcStyle);
                    assertNotEquals(macStyle, pcStyle); // They're different shortcuts

                    // Both should have valid string representations
                    assertNotNull(macStyle.toString());
                    assertNotNull(pcStyle.toString());
                });
    }

    @Test
    @DisplayName("Legacy formats that might exist in old user preferences")
    void testLegacyFormats() {
        // Test edge cases that might exist in old user preference files
        assertDoesNotThrow(
                () -> {
                    // Various combinations that could be stored
                    Shortcut multiModifier = createShortcut("ctrl alt shift F");
                    assertNotNull(multiModifier);

                    // Arrow keys with modifiers
                    Shortcut arrows = createShortcut("meta UP");
                    assertNotNull(arrows);

                    // Function keys with modifiers
                    Shortcut funcKey = createShortcut("shift F12");
                    assertNotNull(funcKey);
                });
    }
}
