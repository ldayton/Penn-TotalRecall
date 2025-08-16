package env;

import info.Constants;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
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
public class Environment {
    private static final Logger logger = LoggerFactory.getLogger(Environment.class);

    // Core environment state
    private final Platform platform;
    private final Properties config;

    // Cached computed values
    private final String userHomeDir;
    private final String aboutMessage;
    private final int chunkSizeInSeconds;

    // Hardcoded constants that were previously configurable
    private static final double INTERPOLATION_TOLERANCE_SECONDS = 0.25;

    public Environment() {
        this.platform = Platform.detect();
        this.userHomeDir = System.getProperty("user.home");
        this.config = loadConfiguration();

        // Compute and cache frequently used values
        this.aboutMessage = buildAboutMessage();
        this.chunkSizeInSeconds = computeChunkSize();
    }

    /** Constructor for testing - allows platform injection, minimal configuration */
    public Environment(Platform platform) {
        this.platform = platform;
        this.userHomeDir = System.getProperty("user.home");
        this.config = new Properties(); // Use empty config for testing

        // Compute and cache frequently used values with defaults
        this.aboutMessage = "Penn TotalRecall Test";
        this.chunkSizeInSeconds = 2; // Default chunk size
    }

    // =============================================================================
    // PLATFORM DETECTION
    // =============================================================================

    public Platform getPlatform() {
        return platform;
    }

    // =============================================================================
    // SYSTEM PATHS
    // =============================================================================

    public String getUserHomeDir() {
        return userHomeDir;
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
    // AUDIO CONFIGURATION
    // =============================================================================

    public int getChunkSizeInSeconds() {
        return chunkSizeInSeconds;
    }

    public int getMaxInterpolatedPixels() {
        return 10; // Hardcoded: reasonable interpolation error tolerance for all platforms
    }

    public double getInterpolationToleratedErrorZoneInSec() {
        return INTERPOLATION_TOLERANCE_SECONDS;
    }

    private int computeChunkSize() {
        return 10; // Hardcoded: audio.chunk_size_seconds=10
    }

    // =============================================================================
    // UI CONFIGURATION
    // =============================================================================

    public boolean shouldUseAWTFileChoosers() {
        boolean defaultValue = platform == Platform.MACOS;
        return getBooleanProperty("ui.use_native_file_choosers", defaultValue);
    }

    public String getPreferencesString() {
        String defaultValue =
                switch (platform) {
                    case MACOS -> "Preferences";
                    case WINDOWS -> "Options";
                    case LINUX -> "Preferences";
                };
        return config.getProperty("ui.preferences_menu_title", defaultValue);
    }

    // =============================================================================
    // PLATFORM-SPECIFIC BEHAVIORS
    // =============================================================================

    /**
     * Applies platform-specific audio workarounds. Currently handles Windows audio thread timing
     * issues.
     */
    public void applyAudioWorkarounds() {
        if (platform == Platform.WINDOWS) {
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
        if (platform == Platform.LINUX && errorCode == -1) {
            return baseMessage
                    + "\n"
                    + Constants.programName
                    + " prefers exclusive access to the sound system.\n"
                    + "Please close all sound-emitting programs and web pages and try again.";
        }
        return baseMessage + "Unspecified error.";
    }

    /**
     * Gets the appropriate application icon path for the platform. Modern platforms support larger
     * icons for better display quality.
     */
    public String getAppIconPath() {
        return switch (platform) {
            case WINDOWS -> "/images/headphones48.png"; // Modern Windows taskbar
            case MACOS, LINUX -> "/images/headphones16.png";
        };
    }

    // =============================================================================
    // WAVEFORM AND RENDERING CONFIGURATION
    // =============================================================================

    // =============================================================================
    // FMOD CONFIGURATION
    // =============================================================================

    public LibraryLoadingMode getFmodLoadingMode() {
        String mode = config.getProperty("fmod.loading.mode", "packaged");
        try {
            return LibraryLoadingMode.valueOf(mode.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid FMOD loading mode '{}', defaulting to PACKAGED", mode);
            return LibraryLoadingMode.PACKAGED;
        }
    }

    public FmodLibraryType getFmodLibraryType() {
        String type = config.getProperty("fmod.library.type", "standard");
        try {
            return FmodLibraryType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid FMOD library type '{}', defaulting to STANDARD", type);
            return FmodLibraryType.STANDARD;
        }
    }

    /** Gets the custom FMOD library path for the specified platform. */
    public String getFmodLibraryPath(Platform platform) {
        String key =
                switch (platform) {
                    case MACOS -> "fmod.library.path.macos";
                    case LINUX -> "fmod.library.path.linux";
                    case WINDOWS -> "fmod.library.path.windows";
                };
        return config.getProperty(key);
    }

    /**
     * @deprecated Use getFmodLibraryPath(Platform.MACOS) instead
     */
    @Deprecated
    public String getFmodLibraryPathMacOS() {
        return getFmodLibraryPath(Platform.MACOS);
    }

    /** Gets the platform-specific FMOD library filename based on library type. */
    public String getFmodLibraryFilename(FmodLibraryType libraryType) {
        return switch (platform) {
            case MACOS ->
                    libraryType == FmodLibraryType.LOGGING ? "libfmodL.dylib" : "libfmod.dylib";
            case LINUX -> libraryType == FmodLibraryType.LOGGING ? "libfmodL.so" : "libfmod.so";
            case WINDOWS -> libraryType == FmodLibraryType.LOGGING ? "fmodL.dll" : "fmod.dll";
        };
    }

    /**
     * Gets the full development path to the FMOD library for the current platform and library type.
     */
    public String getFmodLibraryDevelopmentPath(FmodLibraryType libraryType) {
        String platformDir =
                switch (platform) {
                    case MACOS -> "macos";
                    case LINUX -> "linux";
                    case WINDOWS -> "windows";
                };
        String filename = getFmodLibraryFilename(libraryType);
        return "src/main/resources/fmod/" + platformDir + "/" + filename;
    }

    // =============================================================================
    // APPLICATION INFO
    // =============================================================================

    public String getAboutMessage() {
        return aboutMessage;
    }

    private String buildAboutMessage() {
        return Constants.programName
                + " v"
                + Constants.programVersion
                + "\n"
                + "Maintainer: "
                + Constants.maintainerEmail
                + "\n\n"
                + "Released by:\n"
                + Constants.orgName
                + "\n"
                + Constants.orgAffiliationName
                + "\n"
                + Constants.orgHomepage
                + "\n\n"
                + "License: "
                + Constants.license
                + "\n"
                + Constants.licenseSite;
    }

    // =============================================================================
    // CONFIGURATION LOADING
    // =============================================================================

    private Properties loadConfiguration() {
        Properties config = new Properties();

        // 1. Load bundled defaults (lowest priority)
        loadResource(config, "/config/defaults.properties");

        // 2. Load platform-specific overrides (medium priority)
        String platformConfig = "/config/platform/" + platform.name().toLowerCase() + ".properties";
        loadResource(config, platformConfig);

        // 3. Load user configuration file (higher priority)
        loadUserConfiguration(config);

        // 4. System properties override everything (highest priority)
        config.putAll(System.getProperties());

        logger.debug("Configuration loaded with {} properties", config.size());
        return config;
    }

    private void loadResource(Properties config, String resourcePath) {
        try (InputStream is = Environment.class.getResourceAsStream(resourcePath)) {
            if (is != null) {
                config.load(is);
                logger.debug("Loaded configuration: {}", resourcePath);
            } else {
                logger.debug("Configuration not found: {}", resourcePath);
            }
        } catch (IOException e) {
            logger.warn("Failed to load configuration: {}", resourcePath, e);
        }
    }

    private void loadUserConfiguration(Properties config) {
        File userConfigFile = getConfigDirectory().resolve("application.properties").toFile();
        if (userConfigFile.exists() && userConfigFile.canRead()) {
            try (FileInputStream fis = new FileInputStream(userConfigFile)) {
                config.load(fis);
                logger.debug("Loaded user configuration from {}", userConfigFile.getAbsolutePath());
            } catch (IOException e) {
                logger.warn(
                        "Failed to load user configuration from {}: {}",
                        userConfigFile.getAbsolutePath(),
                        e.getMessage());
            }
        } else {
            logger.debug("User configuration file not found: {}", userConfigFile.getAbsolutePath());
        }
    }

    // =============================================================================
    // UTILITY METHODS
    // =============================================================================

    private int getIntProperty(String key, int defaultValue) {
        String value = config.getProperty(key);
        try {
            return value != null ? Integer.parseInt(value) : defaultValue;
        } catch (NumberFormatException e) {
            logger.warn(
                    "Invalid integer value for '{}': '{}', using default: {}",
                    key,
                    value,
                    defaultValue);
            return defaultValue;
        }
    }

    private double getDoubleProperty(String key, double defaultValue) {
        String value = config.getProperty(key);
        try {
            return value != null ? Double.parseDouble(value) : defaultValue;
        } catch (NumberFormatException e) {
            logger.warn(
                    "Invalid double value for '{}': '{}', using default: {}",
                    key,
                    value,
                    defaultValue);
            return defaultValue;
        }
    }

    private boolean getBooleanProperty(String key, boolean defaultValue) {
        String value = config.getProperty(key);
        if (value == null) return defaultValue;
        return Boolean.parseBoolean(value);
    }

    // =============================================================================
    // PLATFORM BEHAVIOR METHODS
    // =============================================================================

    /**
     * Whether this platform should show Preferences/About in application menus. On Mac, these are
     * handled by the system menu bar.
     */
    public boolean shouldShowPreferencesInMenu() {
        return platform != Platform.MACOS;
    }
}
