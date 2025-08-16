package env;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.Getter;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central environment and platform configuration class.
 *
 * <p>Replaces SysInfo and consolidates all platform-specific behavior, configuration loading, and
 * environment detection into one place.
 *
 * <p>This is a singleton that loads configuration on first access and provides all
 * environment-related functionality.
 */
@Singleton
public class Environment {
    private static final Logger logger = LoggerFactory.getLogger(Environment.class);

    // Core environment state
    private final Platform.PlatformType platform;
    private final AppConfig appConfig;

    // Cached computed values
    @Getter private final String userHomeDir;

    // Configuration keys for update checking
    private static final String RELEASES_API_URL_KEY = "releases.api.url";
    private static final String RELEASES_PAGE_URL_KEY = "releases.page.url";

    @Inject
    public Environment(@NonNull AppConfig appConfig, @NonNull Platform platformService) {
        this(platformService.detect(), appConfig);
    }

    /** Constructor for testing - allows platform injection */
    public Environment(@NonNull Platform.PlatformType platform, @NonNull AppConfig appConfig) {
        this.platform = platform;
        this.userHomeDir = System.getProperty("user.home");
        this.appConfig = appConfig;
    }

    public Path getConfigDirectory() {
        return switch (platform) {
            case MACOS ->
                    Paths.get(userHomeDir, "Library", "Application Support", "Penn TotalRecall");
            case WINDOWS -> Paths.get(System.getenv("APPDATA"), "Penn TotalRecall");
            case LINUX -> Paths.get(userHomeDir, ".penn-totalrecall");
        };
    }

    // =============================================================================
    // PLATFORM-SPECIFIC BEHAVIORS
    // =============================================================================

    /**
     * Applies platform-specific audio workarounds. Currently handles Windows audio thread timing
     * issues.
     */
    public void applyAudioWorkarounds() {
        if (platform == Platform.PlatformType.WINDOWS) {
            // Fix Issue 9 - Windows needs extra sleep after playback stops
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                logger.debug("Sleep interrupted during Windows audio workaround", e);
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Formats platform-specific audio error messages. */
    public String formatAudioError(int errorCode, String baseMessage) {
        if (platform == Platform.PlatformType.LINUX && errorCode == -1) {
            return baseMessage
                    + "\n"
                    + "Penn TotalRecall"
                    + " prefers exclusive access to the sound system.\n"
                    + "Please close all sound-emitting programs and web pages and try again.";
        }
        return baseMessage + "Unspecified error.";
    }

    /**
     * Gets the GitHub Releases API URL for update checking.
     *
     * @return the releases API URL
     */
    public String getReleasesApiUrl() {
        return appConfig.getProperty(RELEASES_API_URL_KEY);
    }

    /**
     * Gets the GitHub Releases page URL for user downloads.
     *
     * @return the releases page URL
     */
    public String getReleasesPageUrl() {
        return appConfig.getProperty(RELEASES_PAGE_URL_KEY);
    }

    @com.google.common.annotations.VisibleForTesting
    String getStringProperty(String key, String defaultValue) {
        return appConfig.getProperty(key, defaultValue);
    }
}
