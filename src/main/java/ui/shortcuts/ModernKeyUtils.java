package ui.shortcuts;

import javax.swing.KeyStroke;
import lombok.NonNull;

/**
 * Zen utility for converting KeyStroke to internal form.
 *
 * <p>Pure function that leverages Java's built-in KeyStroke.toString() while handling edge cases
 * that fail round-trip parsing.
 *
 * <p>Immutable, thread-safe, never returns null.
 */
public final class ModernKeyUtils {

    private ModernKeyUtils() {}

    /**
     * Convert KeyStroke to internal form compatible with KeyStroke.getKeyStroke() parsing.
     *
     * @param key the KeyStroke to convert, never null
     * @return internal form string with trailing space, never null
     */
    public static String getInternalForm(@NonNull KeyStroke key) {
        String representation = key.toString();
        return isRoundTripCompatible(key, representation)
                ? representation + " "
                : "pressed UNKNOWN ";
    }

    /**
     * Test if a KeyStroke representation can round-trip through parsing.
     *
     * @param original the original KeyStroke
     * @param representation the string representation
     * @return true if round-trip parsing preserves the KeyStroke
     */
    private static boolean isRoundTripCompatible(
            @NonNull KeyStroke original, @NonNull String representation) {
        KeyStroke parsed = KeyStroke.getKeyStroke(representation);
        return original.equals(parsed);
    }
}
