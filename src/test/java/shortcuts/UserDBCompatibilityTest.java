package shortcuts;

import static org.junit.jupiter.api.Assertions.*;

import env.Environment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests to ensure existing user preferences stored in UserDB continue to work. */
class UserDBCompatibilityTest {

    private final Environment env = new Environment();

    @Test
    @DisplayName("Existing internal formats from user preferences should parse correctly")
    void testExistingInternalFormatsWork() {
        // Test common internal formats that could be in user preferences
        assertDoesNotThrow(
                () -> {
                    // Mac-style shortcuts (meta = Command)
                    Shortcut macCommand = Shortcut.fromInternalForm("meta shift D", env);
                    assertNotNull(macCommand);

                    Shortcut macControl = Shortcut.fromInternalForm("ctrl S", env);
                    assertNotNull(macControl);

                    // PC-style shortcuts (ctrl = Control)
                    Shortcut pcControl = Shortcut.fromInternalForm("ctrl shift D", env);
                    assertNotNull(pcControl);

                    Shortcut pcAlt = Shortcut.fromInternalForm("alt LEFT", env);
                    assertNotNull(pcAlt);

                    // Common modifier combinations
                    Shortcut shiftOnly = Shortcut.fromInternalForm("shift RIGHT", env);
                    assertNotNull(shiftOnly);

                    Shortcut noModifier = Shortcut.fromInternalForm("F1", env);
                    assertNotNull(noModifier);

                    // Special keys
                    Shortcut delete = Shortcut.fromInternalForm("meta DELETE", env);
                    assertNotNull(delete);

                    Shortcut enter = Shortcut.fromInternalForm("meta shift ENTER", env);
                    assertNotNull(enter);
                });
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
            Shortcut original = Shortcut.fromInternalForm(internalForm, env);

            // Simulate what UserDB.store() saves
            String storedForm = original.getInternalForm();

            // Simulate what UserDB.retrieve() loads
            Shortcut restored = Shortcut.fromInternalForm(storedForm, env);

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
                    Shortcut macStyle =
                            Shortcut.fromInternalForm("meta S", env); // Command+S on Mac
                    Shortcut pcStyle = Shortcut.fromInternalForm("ctrl S", env); // Ctrl+S on PC

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
                    Shortcut multiModifier = Shortcut.fromInternalForm("ctrl alt shift F", env);
                    assertNotNull(multiModifier);

                    // Arrow keys with modifiers
                    Shortcut arrows = Shortcut.fromInternalForm("meta UP", env);
                    assertNotNull(arrows);

                    // Function keys with modifiers
                    Shortcut funcKey = Shortcut.fromInternalForm("shift F12", env);
                    assertNotNull(funcKey);
                });
    }
}
