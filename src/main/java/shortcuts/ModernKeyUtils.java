package shortcuts;

import java.util.Objects;
import javax.swing.KeyStroke;

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
    public static String getInternalForm(KeyStroke key) {
        Objects.requireNonNull(key);
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
    private static boolean isRoundTripCompatible(KeyStroke original, String representation) {
        Objects.requireNonNull(original);
        Objects.requireNonNull(representation);
        KeyStroke parsed = KeyStroke.getKeyStroke(representation);
        return original.equals(parsed);
    }
}
