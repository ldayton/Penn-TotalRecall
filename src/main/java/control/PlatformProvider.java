package control;

import java.util.List;
import java.util.Locale;

/** Platform abstraction for keyboard shortcut behavior. */
public interface PlatformProvider {

    /**
     * @return the separator used between keys for shortcut display
     */
    String getKeySeparator();

    /**
     * @param internalKey the internal key representation
     * @return the platform-specific display symbol, or null if no mapping
     */
    String getKeySymbol(String internalKey);

    /**
     * @return the preferred ordering of modifier keys for this platform
     */
    List<String> getKeyOrder();

    /**
     * Converts external key form to internal form, handling platform-specific mappings.
     *
     * @param externalKey the external key name (e.g., "menu", "command")
     * @return the internal key name
     */
    String externalToInternalForm(String externalKey);

    // ========== Factory Methods ==========

    /**
     * Detects and creates the appropriate platform provider.
     *
     * @return platform provider for the current system
     */
    static PlatformProvider detect() {
        String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);

        if (osName.contains("mac")) {
            return new MacPlatform();
        } else {
            // For now, use WindowsPlatform for all non-Mac systems to preserve original behavior
            // Both WindowsPlatform and LinuxPlatform inherit identical behavior from PCPlatform
            return new WindowsPlatform();
        }
    }
}
