package env;

import com.google.common.annotations.VisibleForTesting;
import jakarta.inject.Inject;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.function.Function;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration loading with hierarchical priority: System properties > User config > Bundled
 * defaults. Thread-safe with lazy initialization. Use manager classes for domain-specific
 * configuration access.
 */
@VisibleForTesting
public class AppConfig {
    private static final Logger logger = LoggerFactory.getLogger(AppConfig.class);

    private static final String CONFIG_FILE_NAME = "application.properties";
    private static final String BUNDLED_CONFIG_PATH = "/" + CONFIG_FILE_NAME;
    private static final String DEFAULTS_CONFIG_PATH = "/config/defaults.properties";

    private final Properties properties;
    private final Platform platform;

    @Inject
    public AppConfig(@NonNull Platform platform) {
        this.platform = platform;
        this.properties = loadConfiguration();
    }

    /** Default constructor for non-DI usage (tests) */
    public AppConfig() {
        this.platform = new Platform();
        this.properties = loadConfiguration();
    }

    /** Gets configuration property with optional default value. */
    public String getProperty(@NonNull String key, @NonNull String defaultValue) {
        return getPropertyWithSystemOverride(key, defaultValue);
    }

    /** Gets configuration property. */
    public String getProperty(@NonNull String key) {
        return getPropertyWithSystemOverride(key);
    }

    /** Gets a boolean property with optional default value. */
    public boolean getBooleanProperty(@NonNull String key, boolean defaultValue) {
        var value = getPropertyWithSystemOverride(key);
        return value != null ? Boolean.parseBoolean(value) : defaultValue;
    }

    /** Gets an integer property with optional default value. */
    @SuppressWarnings("unused")
    public int getIntProperty(@NonNull String key, int defaultValue) {
        return parseProperty(key, defaultValue, Integer::parseInt, "integer");
    }

    /** Gets a double property with optional default value. */
    @SuppressWarnings("unused")
    public double getDoubleProperty(@NonNull String key, double defaultValue) {
        return parseProperty(key, defaultValue, Double::parseDouble, "double");
    }

    /** Gets a property, checking system properties first, then loaded configuration. */
    private String getPropertyWithSystemOverride(
            @NonNull String key, @NonNull String defaultValue) {
        return System.getProperty(key, properties.getProperty(key, defaultValue));
    }

    /** Gets a property, checking system properties first, then loaded configuration. */
    private String getPropertyWithSystemOverride(@NonNull String key) {
        return System.getProperty(key, properties.getProperty(key));
    }

    private <T> T parseProperty(
            @NonNull String key,
            @NonNull T defaultValue,
            @NonNull Function<String, T> parser,
            @NonNull String typeName) {
        var value = getPropertyWithSystemOverride(key);
        if (value == null) return defaultValue;
        try {
            return parser.apply(value);
        } catch (NumberFormatException e) {
            logger.warn(
                    "Invalid {} value for '{}': '{}', using default: {}",
                    typeName,
                    key,
                    value,
                    defaultValue);
            return defaultValue;
        }
    }

    /** Loads configuration from multiple sources in priority order. */
    private Properties loadConfiguration() {
        var config = new Properties();
        loadDefaultsConfiguration(config); // 1. Load base defaults (lowest priority)
        loadBundledDefaults(config); // 2. Load bundled application config (low-medium priority)
        loadPlatformConfiguration(config); // 3. Load platform-specific overrides (medium priority)
        loadUserConfiguration(config); // 4. Load user configuration file (higher priority)
        // 5. System properties override everything (handled in getPropertyWithSystemOverride)
        logger.debug("Configuration loaded with {} properties", config.size());
        return config;
    }

    /** Loads base defaults configuration. */
    private void loadDefaultsConfiguration(@NonNull Properties config) {
        try (var is = AppConfig.class.getResourceAsStream(DEFAULTS_CONFIG_PATH)) {
            if (is != null) {
                config.load(is);
                logger.debug("Loaded defaults configuration from {}", DEFAULTS_CONFIG_PATH);
            } else {
                logger.debug("Defaults configuration not found: {}", DEFAULTS_CONFIG_PATH);
            }
        } catch (IOException e) {
            logger.warn("Failed to load defaults configuration: {}", e.getMessage());
        }
    }

    /** Loads bundled application configuration. */
    private void loadBundledDefaults(@NonNull Properties config) {
        try (var is = AppConfig.class.getResourceAsStream(BUNDLED_CONFIG_PATH)) {
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

    /** Loads platform-specific configuration overrides. */
    private void loadPlatformConfiguration(@NonNull Properties config) {
        var platformType = platform.detect();
        var platformConfigPath =
                "/config/platform/" + platformType.name().toLowerCase() + ".properties";
        try (var is = AppConfig.class.getResourceAsStream(platformConfigPath)) {
            if (is != null) {
                config.load(is);
                logger.debug("Loaded platform configuration from {}", platformConfigPath);
            } else {
                logger.debug("Platform configuration not found: {}", platformConfigPath);
            }
        } catch (IOException e) {
            logger.warn("Failed to load platform configuration: {}", e.getMessage());
        }
    }

    /** Loads user configuration from platform-specific config directory. */
    private void loadUserConfiguration(@NonNull Properties config) {
        var userConfigFile = getUserConfigFile();
        if (userConfigFile.exists() && userConfigFile.canRead()) {
            try (var fis = new FileInputStream(userConfigFile)) {
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

    /** Gets the platform-specific user configuration file path. */
    private File getUserConfigFile() {
        var platformType = platform.detect();
        var userHome = System.getProperty("user.home");
        var configDir =
                switch (platformType) {
                    case MACOS -> userHome + "/Library/Application Support/Penn TotalRecall";
                    case WINDOWS -> System.getenv("APPDATA") + "\\Penn TotalRecall";
                    case LINUX -> userHome + "/.penn-totalrecall";
                };
        var configDirFile = new File(configDir);
        if (!configDirFile.exists() && !configDirFile.mkdirs()) {
            logger.warn("Failed to create config directory: {}", configDir);
        }
        return new File(configDirFile, CONFIG_FILE_NAME);
    }
}
