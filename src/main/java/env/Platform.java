package env;

import jakarta.inject.Singleton;
import java.util.Locale;

/**
 * Platform detection service.
 *
 * <p>Detects the operating system once at startup and provides that information to other components
 * via dependency injection.
 */
@Singleton
public class Platform {

    /** Supported platform types. */
    public enum PlatformType {
        MACOS,
        WINDOWS,
        LINUX
    }

    private final PlatformType detectedPlatform;

    /** Detects the current platform based on the os.name system property. */
    public Platform() {
        String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (osName.contains("mac")) {
            this.detectedPlatform = PlatformType.MACOS;
        } else if (osName.contains("win")) {
            this.detectedPlatform = PlatformType.WINDOWS;
        } else {
            this.detectedPlatform = PlatformType.LINUX;
        }
    }

    /**
     * Constructor for testing - allows injecting a specific platform type.
     *
     * @param platformType the platform type to use
     */
    public Platform(PlatformType platformType) {
        this.detectedPlatform = platformType;
    }

    /**
     * Returns the detected platform type.
     *
     * @return the current platform
     */
    public PlatformType detect() {
        return detectedPlatform;
    }
}
