package env;

import com.google.common.annotations.VisibleForTesting;
import java.util.Locale;

/**
 * Platform detection for cross-platform behavior.
 *
 * <p>Provides clean platform detection without runtime uncertainty. Linux serves as the safe
 * default for all Unix-like systems.
 *
 * <p>Note: This is package-private but made public solely for testing purposes. Production code
 * should use Environment methods instead of accessing Platform directly.
 */
@VisibleForTesting
public enum Platform {
    MACOS,
    WINDOWS,
    LINUX;

    /**
     * Detects the current platform based on os.name system property.
     *
     * @return the current platform (never null)
     */
    public static Platform detect() {
        String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (osName.contains("mac")) return MACOS;
        if (osName.contains("win")) return WINDOWS;
        return LINUX; // Safe default for Unix-like systems (FreeBSD, OpenBSD, Solaris, AIX, etc.)
    }
}
