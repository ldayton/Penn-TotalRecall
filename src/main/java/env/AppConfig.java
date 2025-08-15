package env;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Application configuration management using Java Properties with hierarchical loading.
 *
 * <p>Configuration is loaded from multiple sources in priority order:
 *
 * <ol>
 *   <li>System properties (highest priority, e.g., -Dfmod.loading.mode=unpackaged)
 *   <li>User configuration file in platform-specific config directory
 *   <li>Bundled default configuration in JAR resources (lowest priority)
 * </ol>
 *
 * <p>This class is thread-safe and uses lazy initialization.
 */
public class AppConfig {
    private static final Logger logger = LoggerFactory.getLogger(AppConfig.class);

    private static final String CONFIG_FILE_NAME = "application.properties";
    private static final String BUNDLED_CONFIG_PATH = "/" + CONFIG_FILE_NAME;

    // Configuration keys
    private static final String FMOD_LOADING_MODE_KEY = "fmod.loading.mode";
    private static final String FMOD_LIBRARY_TYPE_KEY = "fmod.library.type";
    private static final String FMOD_LIBRARY_PATH_MACOS_KEY = "fmod.library.path.macos";
    private static final String FMOD_LIBRARY_PATH_LINUX_KEY = "fmod.library.path.linux";
    private static final String FMOD_LIBRARY_PATH_WINDOWS_KEY = "fmod.library.path.windows";

    private final Properties properties;

    public AppConfig() {
        this.properties = loadConfiguration();
    }

    /**
     * Gets the FMOD library loading mode.
     *
     * @return the loading mode, defaults to PACKAGED if not configured
     */
    public LibraryLoadingMode getFmodLoadingMode() {
        String mode = getPropertyWithSystemOverride(FMOD_LOADING_MODE_KEY, "packaged");
        try {
            return LibraryLoadingMode.valueOf(mode.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warn(
                    "Invalid FMOD loading mode '{}', defaulting to PACKAGED. Valid values: {}",
                    mode,
                    java.util.Arrays.toString(LibraryLoadingMode.values()));
            return LibraryLoadingMode.PACKAGED;
        }
    }

    /**
     * Gets the FMOD library type (standard or logging).
     *
     * @return the library type, defaults to STANDARD if not configured
     */
    public FmodLibraryType getFmodLibraryType() {
        String type = getPropertyWithSystemOverride(FMOD_LIBRARY_TYPE_KEY, "standard");
        try {
            return FmodLibraryType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warn(
                    "Invalid FMOD library type '{}', defaulting to STANDARD. Valid values: {}",
                    type,
                    java.util.Arrays.toString(FmodLibraryType.values()));
            return FmodLibraryType.STANDARD;
        }
    }

    /**
     * Gets the custom FMOD library path for the specified platform.
     *
     * @param platform the target platform
     * @return the library path, or null if not configured
     */
    public String getFmodLibraryPath(Platform platform) {
        String key =
                switch (platform) {
                    case MACOS -> FMOD_LIBRARY_PATH_MACOS_KEY;
                    case LINUX -> FMOD_LIBRARY_PATH_LINUX_KEY;
                    case WINDOWS -> FMOD_LIBRARY_PATH_WINDOWS_KEY;
                };
        return getPropertyWithSystemOverride(key);
    }

    /**
     * Gets the FMOD library path for macOS development mode.
     *
     * @deprecated Use getFmodLibraryPath(Platform.MACOS) instead
     * @return the library path, or null if not configured
     */
    @Deprecated
    public String getFmodLibraryPathMacOS() {
        return getFmodLibraryPath(Platform.MACOS);
    }

    /**
     * Gets a configuration property with optional default value.
     *
     * @param key the property key
     * @param defaultValue the default value if property is not found
     * @return the property value or default
     */
    public String getProperty(String key, String defaultValue) {
        return getPropertyWithSystemOverride(key, defaultValue);
    }

    /**
     * Gets a configuration property.
     *
     * @param key the property key
     * @return the property value or null if not found
     */
    public String getProperty(String key) {
        return getPropertyWithSystemOverride(key);
    }

    /**
     * Loads configuration from multiple sources in priority order.
     *
     * @return merged properties from all sources
     */
    private Properties loadConfiguration() {
        Properties config = new Properties();

        // 1. Load bundled defaults (lowest priority)
        loadBundledDefaults(config);

        // 2. Load user configuration file (medium priority)
        loadUserConfiguration(config);

        // 3. System properties override everything (highest priority)
        // Properties.getProperty() already checks system properties first

        logger.debug("Configuration loaded with {} properties", config.size());
        return config;
    }

    /**
     * Loads default configuration bundled in the JAR.
     *
     * @param config properties to populate
     */
    private void loadBundledDefaults(Properties config) {
        try (InputStream is = AppConfig.class.getResourceAsStream(BUNDLED_CONFIG_PATH)) {
            if (is != null) {
                config.load(is);
                logger.debug("Loaded bundled configuration from {}", BUNDLED_CONFIG_PATH);
            } else {
                logger.warn("Bundled configuration not found: {}", BUNDLED_CONFIG_PATH);
            }
        } catch (IOException e) {
            logger.warn("Failed to load bundled configuration: {}", e.getMessage());
        }
    }

    /**
     * Loads user configuration from platform-specific config directory.
     *
     * @param config properties to populate (will override existing keys)
     */
    private void loadUserConfiguration(Properties config) {
        File userConfigFile = getUserConfigFile();
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

    /**
     * Gets the platform-specific user configuration file path.
     *
     * @return the user config file
     */
    private File getUserConfigFile() {
        String configDir;
        Platform platform = Platform.detect();
        if (platform == Platform.MACOS) {
            configDir =
                    System.getProperty("user.home")
                            + "/Library/Application Support/Penn TotalRecall";
        } else if (platform == Platform.WINDOWS) {
            configDir = System.getenv("APPDATA") + "\\Penn TotalRecall";
        } else {
            // Linux/Unix
            configDir = System.getProperty("user.home") + "/.penn-totalrecall";
        }

        File configDirFile = new File(configDir);
        if (!configDirFile.exists()) {
            configDirFile.mkdirs();
        }

        return new File(configDirFile, CONFIG_FILE_NAME);
    }

    /**
     * Gets a property, checking system properties first, then loaded configuration.
     *
     * @param key the property key
     * @param defaultValue the default value
     * @return the property value
     */
    private String getPropertyWithSystemOverride(String key, String defaultValue) {
        // System properties have highest priority
        String systemValue = System.getProperty(key);
        if (systemValue != null) {
            return systemValue;
        }

        // Fall back to loaded configuration
        return properties.getProperty(key, defaultValue);
    }

    /**
     * Gets a property, checking system properties first, then loaded configuration.
     *
     * @param key the property key
     * @return the property value or null
     */
    private String getPropertyWithSystemOverride(String key) {
        return getPropertyWithSystemOverride(key, null);
    }
}
