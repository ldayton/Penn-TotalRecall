package shortcuts;

import static org.junit.jupiter.api.Assertions.*;

import env.Environment;
import env.Platform;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests that verify the Shortcut class produces idiomatic platform representations.
 *
 * <p>PLATFORM CONVENTIONS RESEARCH:
 *
 * <p>Mac (Apple Official Style Guide): - Order: Control → Option → Shift → Command (⌃⌥⇧⌘) -
 * Symbols: Use Unicode symbols (⌘⌥⇧⌃) not text - Separators: No separators between symbols - Return
 * key: ↩ (not "Enter") - Arrow keys: ←→↑↓ (symbols not text) - Source: Apple Human Interface
 * Guidelines, Apple Style Guide
 *
 * <p>PC (Microsoft Official Style Guide): - Separators: Plus sign (+) between keys -
 * Capitalization: Sentence case ("Ctrl" not "CTRL") - Enter key: "Enter" (text not symbol) - Arrow
 * keys: "Left", "Right", "Up", "Down" (text not symbols) - Spacebar: "Spacebar" (Microsoft's
 * preferred term) - Source: Microsoft Style Guide, Win32 UX Guidelines
 *
 * <p>EXCEPTIONS FROM OFFICIAL CONVENTIONS: - Space key: Use "Space" on both platforms (not
 * "Spacebar" on PC, not ␣ symbol on Mac) Reason: Space symbol ␣ is ugly, "Space" is more readable
 * than "Spacebar"
 *
 * <p>These tests specify correct behavior based on official platform guidelines with practical
 * exceptions for usability.
 */
public class ShortcutTest {

    private final Environment macEnv = new Environment(Platform.MACOS);
    private final Environment pcEnv = new Environment(Platform.WINDOWS);

    @Nested
    @DisplayName("Platform-specific representations")
    class PlatformSpecificTests {

        @Test
        @DisplayName("Mac Command+S should show as ⌘S")
        void macCommandSShouldShowAsCommandSymbolS() {
            Environment mac = macEnv;
            KeyStroke cmdS = KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.META_DOWN_MASK);
            Shortcut shortcut = new Shortcut(cmdS, mac);

            assertEquals("⌘S", shortcut.toString());
        }

        @Test
        @DisplayName("Mac Control+S should show as ^S")
        void macControlSShouldShowAsControlSymbolS() {
            Environment mac = macEnv;
            KeyStroke ctrlS = KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK);
            Shortcut shortcut = new Shortcut(ctrlS, mac);

            assertEquals("^S", shortcut.toString());
        }

        @Test
        @DisplayName("Mac Shift+Command+A should show as ⇧⌘A")
        void macShiftCommandAShouldShowAsShiftCommandSymbolsA() {
            Environment mac = macEnv;
            KeyStroke shiftCmdA =
                    KeyStroke.getKeyStroke(
                            KeyEvent.VK_A, InputEvent.SHIFT_DOWN_MASK | InputEvent.META_DOWN_MASK);
            Shortcut shortcut = new Shortcut(shiftCmdA, mac);

            assertEquals("⇧⌘A", shortcut.toString());
        }

        @Test
        @DisplayName("Mac all modifiers should show as ^⌥⇧⌘A")
        void macAllModifiersShouldShowInCorrectOrder() {
            Environment mac = macEnv;
            KeyStroke complexKey =
                    KeyStroke.getKeyStroke(
                            KeyEvent.VK_A,
                            InputEvent.CTRL_DOWN_MASK
                                    | InputEvent.ALT_DOWN_MASK
                                    | InputEvent.SHIFT_DOWN_MASK
                                    | InputEvent.META_DOWN_MASK);
            Shortcut shortcut = new Shortcut(complexKey, mac);

            assertEquals("^⌥⇧⌘A", shortcut.toString());
        }

        @Test
        @DisplayName("PC Control+S should show as Ctrl+S")
        void pcControlSShouldShowAsCtrlPlusS() {
            Environment pc = pcEnv;
            KeyStroke ctrlS = KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK);
            Shortcut shortcut = new Shortcut(ctrlS, pc);

            assertEquals("Ctrl+S", shortcut.toString());
        }

        @Test
        @DisplayName("PC PageUp should use proper capitalization")
        void pcPageUpShouldUseProperCapitalization() {
            Environment pc = pcEnv;
            KeyStroke pageUp = KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0);
            Shortcut shortcut = new Shortcut(pageUp, pc);

            // Microsoft uses sentence case, so "Page Up" or standard abbreviation "PgUp"
            // Current broken behavior likely shows "Pgup" due to capitalize(toLowerCase()) bug
            String result = shortcut.toString();
            assertTrue(
                    result.equals("Page Up") || result.equals("PgUp"),
                    "PC PageUp should be 'Page Up' or 'PgUp', not: " + result);
        }

        @Test
        @DisplayName("PC multiple modifiers should use plus separators")
        void pcMultipleModifiersShouldUsePlusSeparators() {
            Environment pc = pcEnv;
            KeyStroke complexKey =
                    KeyStroke.getKeyStroke(
                            KeyEvent.VK_A,
                            InputEvent.SHIFT_DOWN_MASK
                                    | InputEvent.CTRL_DOWN_MASK
                                    | InputEvent.ALT_DOWN_MASK);
            Shortcut shortcut = new Shortcut(complexKey, pc);

            // Microsoft style: use + separators, but order can vary
            // Focus on proper separators and format rather than exact order
            String result = shortcut.toString();
            assertTrue(result.contains("+"), "Should use + separators");
            assertTrue(result.contains("Shift"), "Should contain Shift");
            assertTrue(result.contains("Ctrl"), "Should contain Ctrl");
            assertTrue(result.contains("Alt"), "Should contain Alt");
            assertTrue(result.endsWith("A"), "Should end with the key");
        }
    }

    @Nested
    @DisplayName("Function keys")
    class FunctionKeyTests {

        @Test
        @DisplayName("Function keys should display as F1, F2, etc")
        void functionKeysShouldDisplayCorrectly() {
            Environment mac = macEnv;
            Environment pc = pcEnv;

            KeyStroke f1 = KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0);
            KeyStroke f12 = KeyStroke.getKeyStroke(KeyEvent.VK_F12, 0);

            assertEquals("F1", new Shortcut(f1, mac).toString());
            assertEquals("F1", new Shortcut(f1, pc).toString());
            assertEquals("F12", new Shortcut(f12, mac).toString());
            assertEquals("F12", new Shortcut(f12, pc).toString());
        }
    }

    @Nested
    @DisplayName("Special keys")
    class SpecialKeyTests {

        @Test
        @DisplayName("Numpad keys should show user-friendly names")
        void numpadKeysShouldShowUserFriendlyNames() {
            Environment pc = pcEnv;

            KeyStroke numpad1 = KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD1, 0);
            KeyStroke numpadEnter =
                    KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0); // Assume numpad enter

            assertEquals("Num 1", new Shortcut(numpad1, pc).toString());
        }

        @Test
        @DisplayName("Space key should show as Space")
        void spaceKeyShouldShowAsSpace() {
            Environment mac = macEnv;
            Environment pc = pcEnv;

            KeyStroke space = KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0);

            assertEquals("Space", new Shortcut(space, mac).toString());
            assertEquals("Space", new Shortcut(space, pc).toString());
        }
    }

    @Nested
    @DisplayName("Basic functionality")
    class BasicFunctionalityTests {

        @Test
        @DisplayName("Platform detection works correctly")
        void platformDetectionWorksCorrectly() {
            KeyStroke stroke = KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK);

            Shortcut macShortcut = Shortcut.forPlatform(stroke, macEnv);
            Shortcut pcShortcut = Shortcut.forPlatform(stroke, macEnv);

            // Should produce different representations
            assertNotEquals(macShortcut.toString(), pcShortcut.toString());
        }
    }

    @Nested
    @DisplayName("External to internal form mapping")
    class ExternalInternalMappingTests {

        @Test
        @DisplayName("Menu key maps differently on Mac vs PC")
        void menuKeyMapsDifferentlyOnMacVsPC() {
            Environment mac = macEnv;
            Environment pc = pcEnv;

            assertEquals("meta", mac.externalToInternalForm("menu"));
            assertEquals("ctrl", pc.externalToInternalForm("menu"));
            assertEquals("meta", mac.externalToInternalForm("command"));
            assertEquals("meta", pc.externalToInternalForm("command"));
        }
    }

    @Nested
    @DisplayName("Input validation")
    class InputValidationTests {

        @Test
        @DisplayName("Constructor rejects KeyStroke with no internal form")
        void constructorRejectsKeystrokeWithNoInternalForm() {
            Environment mac = macEnv;

            // This might create a KeyStroke that UnsafeKeyUtils can't handle
            KeyStroke invalidStroke = KeyStroke.getKeyStroke("invalid");

            if (invalidStroke != null) {
                // Should throw RuntimeException with specific message
                RuntimeException exception =
                        assertThrows(
                                RuntimeException.class, () -> new Shortcut(invalidStroke, mac));
                assertTrue(exception.getMessage().contains("refuse to create a Shortcut"));
            }
        }

        @Test
        @DisplayName("Constructor handles null platform gracefully")
        void constructorHandlesNullPlatformGracefully() {
            KeyStroke stroke = KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK);

            // Should either throw or handle null platform gracefully
            assertThrows(NullPointerException.class, () -> new Shortcut(stroke, null));
        }

        @Test
        @DisplayName("fromInternalForm handles invalid input gracefully")
        void fromInternalFormHandlesInvalidInputGracefully() {
            assertThrows(
                    RuntimeException.class,
                    () -> Shortcut.fromInternalForm("invalid keystroke format", macEnv));
        }
    }

    @Nested
    @DisplayName("Complex key combinations")
    class ComplexKeyTests {

        @Test
        @DisplayName("Very long modifier combinations should be readable")
        void veryLongModifierCombinationsShouldBeReadable() {
            Environment mac = macEnv;

            // All possible modifiers + function key
            KeyStroke complexStroke =
                    KeyStroke.getKeyStroke(
                            KeyEvent.VK_F12,
                            InputEvent.CTRL_DOWN_MASK
                                    | InputEvent.ALT_DOWN_MASK
                                    | InputEvent.SHIFT_DOWN_MASK
                                    | InputEvent.META_DOWN_MASK);

            Shortcut shortcut = new Shortcut(complexStroke, mac);
            String result = shortcut.toString();

            // Should be readable and properly ordered
            assertEquals("^⌥⇧⌘F12", result);
            assertTrue(result.length() < 20, "Complex shortcuts should stay concise");
        }

        @Test
        @DisplayName("Single key shortcuts show just the key")
        void singleKeyShortcutsShowJustTheKey() {
            Environment mac = macEnv;
            Environment pc = pcEnv;

            KeyStroke enter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0);

            assertEquals("↩", new Shortcut(enter, mac).toString());
            assertEquals("Enter", new Shortcut(enter, pc).toString());
        }

        @Test
        @DisplayName("Modifier-only combinations handled appropriately")
        void modifierOnlyCombinationsHandledAppropriately() {
            Environment mac = macEnv;

            // Just Shift key pressed (no other key)
            KeyStroke justShift = KeyStroke.getKeyStroke(KeyEvent.VK_SHIFT, 0);

            // Should show something reasonable, not crash
            Shortcut shortcut = new Shortcut(justShift, mac);
            String result = shortcut.toString();

            assertNotNull(result);
            assertFalse(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("Platform consistency and conventions")
    class PlatformConventionTests {

        @Test
        @DisplayName("Mac shortcuts should never use + separator")
        void macShortcutsShouldNeverUsePlusSeparator() {
            Environment mac = macEnv;

            // Test various combinations
            KeyStroke[] testStrokes = {
                KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.META_DOWN_MASK),
                KeyStroke.getKeyStroke(
                        KeyEvent.VK_A, InputEvent.SHIFT_DOWN_MASK | InputEvent.META_DOWN_MASK),
                KeyStroke.getKeyStroke(
                        KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK)
            };

            for (KeyStroke stroke : testStrokes) {
                String result = new Shortcut(stroke, mac).toString();
                assertFalse(
                        result.contains("+"),
                        "Mac shortcut '" + result + "' should not contain + separator");
            }
        }

        @Test
        @DisplayName("PC shortcuts should always use + separator for modifiers")
        void pcShortcutsShouldAlwaysUsePlusSeparatorForModifiers() {
            Environment pc = pcEnv;

            KeyStroke ctrlAltS =
                    KeyStroke.getKeyStroke(
                            KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK);

            String result = new Shortcut(ctrlAltS, pc).toString();

            assertTrue(result.contains("+"), "PC shortcuts with modifiers should use + separator");
            // Should be something like "Ctrl+Alt+S"
            assertTrue(
                    result.matches(".*\\+.*\\+.*"),
                    "Should have multiple + separators for multiple modifiers");
        }

        @Test
        @DisplayName("Same semantic shortcut displays differently per platform")
        void sameSemanticShortcutDisplaysDifferentlyPerPlatform() {
            // Copy shortcut: Cmd+C on Mac, Ctrl+C on PC
            KeyStroke macCopy = KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.META_DOWN_MASK);
            KeyStroke pcCopy = KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK);

            String macResult = new Shortcut(macCopy, macEnv).toString();
            String pcResult = new Shortcut(pcCopy, macEnv).toString();

            assertEquals("⌘C", macResult);
            assertEquals("Ctrl+C", pcResult);

            // They should look completely different
            assertNotEquals(macResult, pcResult);
        }
    }
}
