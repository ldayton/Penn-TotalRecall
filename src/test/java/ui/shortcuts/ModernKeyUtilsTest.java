package ui.shortcuts;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Test suite verifying that ModernKeyUtils produces correct, parseable KeyStroke strings that
 * follow modern Java conventions and maintain round-trip compatibility.
 */
public class ModernKeyUtilsTest {

    /**
     * Helper method to verify a KeyStroke produces the expected internal form and that the form can
     * be parsed back to an equivalent KeyStroke.
     */
    private void assertInternalForm(KeyStroke keystroke, String expectedForm, String description) {
        String actualForm = ModernKeyUtils.getInternalForm(keystroke);
        assertEquals(expectedForm, actualForm, description + " - internal form");

        // Verify round-trip parsing works
        String cleanForm = actualForm.trim();
        KeyStroke parsed = KeyStroke.getKeyStroke(cleanForm);
        assertNotNull(parsed, description + " - should be parseable");
        assertEquals(keystroke, parsed, description + " - round-trip should preserve KeyStroke");
    }

    @Nested
    @DisplayName("Basic modifier combinations")
    class ModifierCombinations {

        @Test
        @DisplayName("Single modifiers produce correct format")
        void singleModifiers() {
            assertInternalForm(
                    KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK),
                    "ctrl pressed S ",
                    "Ctrl+S");

            assertInternalForm(
                    KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.ALT_DOWN_MASK),
                    "alt pressed A ",
                    "Alt+A");

            assertInternalForm(
                    KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.SHIFT_DOWN_MASK),
                    "shift pressed Z ",
                    "Shift+Z");

            assertInternalForm(
                    KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.META_DOWN_MASK),
                    "meta pressed C ",
                    "Meta+C");
        }

        @Test
        @DisplayName("Multiple modifiers use correct order")
        void multipleModifiers() {
            // Standard order: shift, ctrl, meta, alt
            assertInternalForm(
                    KeyStroke.getKeyStroke(
                            KeyEvent.VK_A, InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK),
                    "shift ctrl pressed A ",
                    "Shift+Ctrl+A");

            assertInternalForm(
                    KeyStroke.getKeyStroke(
                            KeyEvent.VK_F1,
                            InputEvent.CTRL_DOWN_MASK
                                    | InputEvent.ALT_DOWN_MASK
                                    | InputEvent.SHIFT_DOWN_MASK
                                    | InputEvent.META_DOWN_MASK),
                    "shift ctrl meta alt pressed F1 ",
                    "All modifiers+F1");
        }
    }

    @Nested
    @DisplayName("Standard keys")
    class StandardKeys {

        @Test
        @DisplayName("Letters produce uppercase format")
        void letters() {
            assertInternalForm(KeyStroke.getKeyStroke(KeyEvent.VK_A, 0), "pressed A ", "Letter A");

            assertInternalForm(KeyStroke.getKeyStroke(KeyEvent.VK_Z, 0), "pressed Z ", "Letter Z");
        }

        @Test
        @DisplayName("Digits produce correct format")
        void digits() {
            assertInternalForm(KeyStroke.getKeyStroke(KeyEvent.VK_0, 0), "pressed 0 ", "Digit 0");

            assertInternalForm(KeyStroke.getKeyStroke(KeyEvent.VK_9, 0), "pressed 9 ", "Digit 9");
        }

        @Test
        @DisplayName("Function keys use F notation")
        void functionKeys() {
            assertInternalForm(KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0), "pressed F1 ", "F1 key");

            assertInternalForm(
                    KeyStroke.getKeyStroke(KeyEvent.VK_F12, 0), "pressed F12 ", "F12 key");
        }
    }

    @Nested
    @DisplayName("Special keys")
    class SpecialKeys {

        @Test
        @DisplayName("Navigation keys use descriptive names")
        void navigationKeys() {
            assertInternalForm(
                    KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "pressed UP ", "Up arrow");

            assertInternalForm(
                    KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0), "pressed PAGE_UP ", "Page Up");

            assertInternalForm(
                    KeyStroke.getKeyStroke(KeyEvent.VK_HOME, 0), "pressed HOME ", "Home key");
        }

        @Test
        @DisplayName("Editing keys use standard names")
        void editingKeys() {
            assertInternalForm(
                    KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "pressed ENTER ", "Enter key");

            assertInternalForm(
                    KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0),
                    "pressed BACK_SPACE ",
                    "Backspace key");

            assertInternalForm(
                    KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "pressed DELETE ", "Delete key");

            assertInternalForm(
                    KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), "pressed TAB ", "Tab key");

            assertInternalForm(
                    KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "pressed ESCAPE ", "Escape key");
        }

        @Test
        @DisplayName("Space key uses simple name")
        void spaceKey() {
            assertInternalForm(
                    KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "pressed SPACE ", "Space key");
        }
    }

    @Nested
    @DisplayName("Numpad keys")
    class NumpadKeys {

        @Test
        @DisplayName("Numpad digits use NumPad prefix")
        void numpadDigits() {
            assertInternalForm(
                    KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD0, 0), "pressed NUMPAD0 ", "Numpad 0");

            assertInternalForm(
                    KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD9, 0), "pressed NUMPAD9 ", "Numpad 9");
        }

        @Test
        @DisplayName("Numpad operators use descriptive names")
        void numpadOperators() {
            assertInternalForm(
                    KeyStroke.getKeyStroke(KeyEvent.VK_ADD, 0), "pressed ADD ", "Numpad plus");

            assertInternalForm(
                    KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, 0),
                    "pressed SUBTRACT ",
                    "Numpad minus");

            assertInternalForm(
                    KeyStroke.getKeyStroke(KeyEvent.VK_MULTIPLY, 0),
                    "pressed MULTIPLY ",
                    "Numpad multiply");

            assertInternalForm(
                    KeyStroke.getKeyStroke(KeyEvent.VK_DIVIDE, 0),
                    "pressed DIVIDE ",
                    "Numpad divide");
        }
    }

    @Nested
    @DisplayName("Punctuation keys")
    class PunctuationKeys {

        @Test
        @DisplayName("Common punctuation uses readable names")
        void commonPunctuation() {
            assertInternalForm(
                    KeyStroke.getKeyStroke(KeyEvent.VK_COMMA, 0), "pressed COMMA ", "Comma key");

            assertInternalForm(
                    KeyStroke.getKeyStroke(KeyEvent.VK_PERIOD, 0), "pressed PERIOD ", "Period key");

            assertInternalForm(
                    KeyStroke.getKeyStroke(KeyEvent.VK_SLASH, 0), "pressed SLASH ", "Slash key");

            assertInternalForm(
                    KeyStroke.getKeyStroke(KeyEvent.VK_SEMICOLON, 0),
                    "pressed SEMICOLON ",
                    "Semicolon key");
        }
    }

    @Nested
    @DisplayName("System and context keys")
    class SystemKeys {

        @Test
        @DisplayName("Modern system keys are recognized")
        void modernSystemKeys() {
            // These should use Java's modern key recognition, not "unknown"
            KeyStroke contextMenu = KeyStroke.getKeyStroke(KeyEvent.VK_CONTEXT_MENU, 0);
            String result = ModernKeyUtils.getInternalForm(contextMenu);
            assertTrue(
                    result.contains("CONTEXT_MENU"),
                    "Context menu should be recognized, got: " + result);

            KeyStroke windows = KeyStroke.getKeyStroke(KeyEvent.VK_WINDOWS, 0);
            result = ModernKeyUtils.getInternalForm(windows);
            assertTrue(
                    result.contains("WINDOWS"), "Windows key should be recognized, got: " + result);
        }
    }

    @Nested
    @DisplayName("Edge cases and error handling")
    class EdgeCases {

        @Test
        @DisplayName("Unknown keys use fallback format")
        void unknownKeys() {
            // Test with intentionally invalid key codes
            KeyStroke unknown = KeyStroke.getKeyStroke(-1, 0);
            if (unknown != null) {
                String result = ModernKeyUtils.getInternalForm(unknown);
                assertEquals("pressed UNKNOWN ", result, "Unknown keys should use clean fallback");
            }
        }

        @Test
        @DisplayName("Null input throws exception")
        void nullInput() {
            assertThrows(
                    RuntimeException.class,
                    () -> ModernKeyUtils.getInternalForm(null),
                    "Null KeyStroke should throw RuntimeException");
        }

        @Test
        @DisplayName("All outputs end with space for compatibility")
        void outputFormat() {
            KeyStroke test = KeyStroke.getKeyStroke(KeyEvent.VK_A, 0);
            String result = ModernKeyUtils.getInternalForm(test);
            assertTrue(result.endsWith(" "), "Output should end with space for compatibility");
        }
    }

    @Nested
    @DisplayName("Round-trip compatibility")
    class RoundTripCompatibility {

        @Test
        @DisplayName("Common combinations parse correctly")
        void commonCombinations() {
            KeyStroke[] testCases = {
                KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK),
                KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.META_DOWN_MASK),
                KeyStroke.getKeyStroke(KeyEvent.VK_F1, InputEvent.ALT_DOWN_MASK),
                KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0),
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
                KeyStroke.getKeyStroke(
                        KeyEvent.VK_A, InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK)
            };

            for (KeyStroke original : testCases) {
                String internalForm = ModernKeyUtils.getInternalForm(original);
                String cleanForm = internalForm.trim();

                KeyStroke parsed = KeyStroke.getKeyStroke(cleanForm);
                assertNotNull(parsed, "Should be parseable: " + internalForm);
                assertEquals(original, parsed, "Round-trip should preserve: " + internalForm);
            }
        }

        @Test
        @DisplayName("Output format matches Java KeyStroke expectations")
        void outputFormatMatches() {
            KeyStroke test = KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK);
            String modernForm = ModernKeyUtils.getInternalForm(test);
            String javaForm = test.toString();

            // Should be nearly identical (ModernKeyUtils adds trailing space)
            assertEquals(
                    javaForm + " ",
                    modernForm,
                    "ModernKeyUtils should match Java's format with trailing space");
        }
    }
}
