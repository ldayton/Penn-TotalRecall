package env;

import components.MacOSIntegration;
import info.Constants;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
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
    private final int menuKey;
    private final int chunkSizeInSeconds;

    // Hardcoded constants that were previously configurable
    private static final double INTERPOLATION_TOLERANCE_SECONDS = 0.25;

    public Environment() {
        this.platform = Platform.detect();
        this.userHomeDir = System.getProperty("user.home");
        this.config = loadConfiguration();

        // Compute and cache frequently used values
        this.aboutMessage = buildAboutMessage();
        this.menuKey = computeMenuKey();
        this.chunkSizeInSeconds = computeChunkSize();
    }

    /** Constructor for testing - allows platform injection, minimal configuration */
    public Environment(Platform platform) {
        this.platform = platform;
        this.userHomeDir = System.getProperty("user.home");
        this.config = new Properties(); // Use empty config for testing

        // Compute and cache frequently used values with defaults
        this.aboutMessage = "Penn TotalRecall Test";
        this.menuKey = computeMenuKey();
        this.chunkSizeInSeconds = 2; // Default chunk size
    }

    // =============================================================================
    // LOOK AND FEEL INITIALIZATION
    // =============================================================================

    /**
     * Initializes the Look and Feel based on platform and user configuration. Call this early in
     * application startup, before creating any Swing components.
     */
    public void initializeLookAndFeel() {
        configurePlatformProperties();

        String lafClass = getLookAndFeelClassName();
        try {
            UIManager.setLookAndFeel(lafClass);
            logger.debug("Set Look and Feel: {}", lafClass);
        } catch (Exception e) {
            logger.warn(
                    "Failed to set Look and Feel: {}, falling back to system default", lafClass, e);
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception fallbackException) {
                logger.error("Failed to set fallback Look and Feel", fallbackException);
            }
        }

        configureNativeIntegration();
    }

    private void configurePlatformProperties() {
        if (platform == Platform.MACOS) {
            // Configure macOS-specific system properties for optimal rendering
            System.setProperty("apple.laf.useScreenMenuBar", "true");
            System.setProperty("apple.awt.textantialiasing", "on");
            System.setProperty("apple.awt.antialiasing", "on");
            System.setProperty("apple.awt.rendering", "quality");
            System.setProperty("apple.awt.application.appearance", "system");
            System.setProperty("apple.awt.application.name", "Penn TotalRecall");
        }
    }

    private String getLookAndFeelClassName() {
        // Check user configuration first
        String userLaf = config.getProperty("ui.look_and_feel");
        if (userLaf != null && !userLaf.trim().isEmpty()) {
            return userLaf;
        }

        return config.getProperty("ui.look_and_feel", "com.formdev.flatlaf.FlatLightLaf");
    }

    private void configureNativeIntegration() {
        if (platform == Platform.MACOS) {
            try {
                MacOSIntegration.integrateWithMacOS();
                logger.debug("macOS integration configured");
            } catch (Exception e) {
                logger.warn("Failed to configure macOS integration", e);
            }
        }
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

    public int getMenuKey() {
        return menuKey;
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
    // SHORTCUT FORMATTING
    // =============================================================================

    /** Gets the display symbol for a key name (used by Shortcut class). */
    public String getKeySymbol(String key) {
        if (platform == Platform.MACOS) {
            return getMacKeySymbol(key);
        } else {
            return getPcKeySymbol(key);
        }
    }

    /** Converts external key form to internal form (used by Shortcut class). */
    public String externalToInternalForm(String externalKey) {
        // This method converts display names back to internal KeyStroke forms
        // Used when parsing shortcuts from external configuration
        return switch (platform) {
            case MACOS -> macExternalToInternal(externalKey);
            case WINDOWS, LINUX -> pcExternalToInternal(externalKey);
        };
    }

    /** Formats a keyboard shortcut for display according to platform conventions. */
    public String formatShortcut(KeyStroke stroke) {
        return switch (platform) {
            case MACOS -> formatMacShortcut(stroke);
            case WINDOWS, LINUX -> formatPcShortcut(stroke);
        };
    }

    public String getKeySeparator() {
        return switch (platform) {
            case MACOS -> "";
            case WINDOWS, LINUX -> "+";
        };
    }

    public List<String> getKeyOrder() {
        return switch (platform) {
            case MACOS -> List.of("^", "⌥", "⇧", "⌘");
            case WINDOWS, LINUX -> List.of("Shift", "Ctrl", "Alt");
        };
    }

    private String formatMacShortcut(KeyStroke stroke) {
        // Implement Mac-specific shortcut formatting with symbols
        // This would contain the logic from MacPlatform.java
        String result = "";
        int modifiers = stroke.getModifiers();

        if ((modifiers & InputEvent.CTRL_DOWN_MASK) != 0) result += "^";
        if ((modifiers & InputEvent.ALT_DOWN_MASK) != 0) result += "⌥";
        if ((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0) result += "⇧";
        if ((modifiers & InputEvent.META_DOWN_MASK) != 0) result += "⌘";

        // Add key symbol
        String keySymbol = getMacKeySymbol(stroke.getKeyCode());
        if (keySymbol != null) {
            result += keySymbol;
        } else {
            result +=
                    KeyStroke.getKeyStroke(stroke.getKeyCode(), 0)
                            .toString()
                            .replace("pressed ", "");
        }

        return result;
    }

    private String formatPcShortcut(KeyStroke stroke) {
        // Implement PC-specific shortcut formatting with text
        // This would contain the logic from PCPlatform.java
        String result = "";
        int modifiers = stroke.getModifiers();

        if ((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0) result += "Shift+";
        if ((modifiers & InputEvent.CTRL_DOWN_MASK) != 0) result += "Ctrl+";
        if ((modifiers & InputEvent.ALT_DOWN_MASK) != 0) result += "Alt+";

        // Add key name
        String keyName = getPcKeySymbol(stroke.getKeyCode());
        if (keyName != null) {
            result += keyName;
        } else {
            result +=
                    KeyStroke.getKeyStroke(stroke.getKeyCode(), 0)
                            .toString()
                            .replace("pressed ", "");
        }

        return result;
    }

    private String getMacKeySymbol(int keyCode) {
        return switch (keyCode) {
            case KeyEvent.VK_BACK_SPACE -> "⌫";
            case KeyEvent.VK_DELETE -> "⌦";
            case KeyEvent.VK_ENTER -> "↩";
            case KeyEvent.VK_ESCAPE -> "⎋";
            case KeyEvent.VK_HOME -> "\u2196";
            case KeyEvent.VK_END -> "\u2198";
            case KeyEvent.VK_PAGE_UP -> "PgUp";
            case KeyEvent.VK_PAGE_DOWN -> "PgDn";
            case KeyEvent.VK_LEFT -> "←";
            case KeyEvent.VK_RIGHT -> "→";
            case KeyEvent.VK_UP -> "↑";
            case KeyEvent.VK_DOWN -> "↓";
            case KeyEvent.VK_TAB -> "Tab";
            default -> null;
        };
    }

    private String getPcKeySymbol(int keyCode) {
        return switch (keyCode) {
            case KeyEvent.VK_BACK_SPACE -> "BackSpace";
            case KeyEvent.VK_DELETE -> "Del";
            case KeyEvent.VK_ENTER -> "Enter";
            case KeyEvent.VK_ESCAPE -> "Esc";
            case KeyEvent.VK_HOME -> "Home";
            case KeyEvent.VK_END -> "End";
            case KeyEvent.VK_PAGE_UP -> "PgUp";
            case KeyEvent.VK_PAGE_DOWN -> "PgDn";
            case KeyEvent.VK_LEFT -> "Left";
            case KeyEvent.VK_RIGHT -> "Right";
            case KeyEvent.VK_UP -> "Up";
            case KeyEvent.VK_DOWN -> "Down";
            case KeyEvent.VK_TAB -> "Tab";
            default -> null;
        };
    }

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
    // XML-SPECIFIC SHORTCUT METHODS
    // =============================================================================

    /**
     * Converts XML keynames from actions.xml to internal KeyStroke format. This is separate from
     * display formatting and specifically handles the XML schema.
     */
    public String xmlKeynameToInternalForm(String xmlKeyname) {
        // Handle cross-platform "menu" modifier: Command on Mac, Ctrl on Windows/Linux
        if ("menu".equals(xmlKeyname)) {
            return switch (platform) {
                case MACOS -> "meta";
                case WINDOWS, LINUX -> "ctrl";
            };
        }

        // Handle other common XML keynames to internal format
        return switch (xmlKeyname.toLowerCase()) {
            case "alt" -> "alt";
            case "shift" -> "shift";
            case "ctrl" -> "ctrl";
            case "command" -> "meta"; // Explicit command for Mac
                // Key names (non-modifiers) - pass through uppercase
            default -> xmlKeyname.toUpperCase();
        };
    }

    // =============================================================================
    // SHORTCUT HELPER METHODS
    // =============================================================================

    private String getMacKeySymbol(String key) {
        // Returns Mac symbols for keys, null if no specific symbol
        return switch (key.toLowerCase()) {
            case "cmd", "meta" -> "⌘";
            case "option", "alt" -> "⌥";
            case "shift" -> "⇧";
            case "ctrl", "control" -> "^";
            case "tab" -> "⇥";
            case "enter", "return" -> "↩";
            case "delete" -> "⌫";
            case "escape" -> "⎋";
            case "up" -> "↑";
            case "down" -> "↓";
            case "left" -> "←";
            case "right" -> "→";
            default -> null; // Use default key name
        };
    }

    private String getPcKeySymbol(String key) {
        // PC uses text-based key names, no special symbols
        return switch (key.toLowerCase()) {
            case "cmd", "meta" -> "Win";
            case "option" -> "Alt";
            case "alt" -> "Alt";
            case "shift" -> "Shift";
            case "ctrl", "control" -> "Ctrl";
            case "tab" -> "Tab";
            case "enter", "return" -> "Enter";
            case "delete" -> "Del";
            case "space" -> "Space";
            case "escape" -> "Esc";
            case "up" -> "↑";
            case "down" -> "↓";
            case "left" -> "←";
            case "right" -> "→";
            default -> key; // Use key name as-is
        };
    }

    private String macExternalToInternal(String externalKey) {
        // Convert Mac display symbols back to internal KeyStroke format
        return switch (externalKey.toLowerCase()) {
            case "⌘" -> "meta";
            case "⌥" -> "alt";
            case "⇧" -> "shift";
            case "^" -> "ctrl";
            case "⇥" -> "TAB";
            case "↩" -> "ENTER";
            case "⌫" -> "DELETE";
            case "⎋" -> "ESCAPE";
            case "↑" -> "UP";
            case "↓" -> "DOWN";
            case "←" -> "LEFT";
            case "→" -> "RIGHT";
            case "menu" -> "meta"; // Cross-platform "menu" = Command on Mac
            case "command", "cmd" -> "meta";
            case "option" -> "alt";
            case "control", "ctrl" -> "ctrl";
            case "shift" -> "shift";
            case "space" -> "SPACE";
            case "tab" -> "TAB";
            case "enter", "return" -> "ENTER";
            case "delete" -> "DELETE";
            case "escape", "esc" -> "ESCAPE";
            default -> externalKey.toUpperCase(); // Uppercase key names (A, B, C, etc.)
        };
    }

    private String pcExternalToInternal(String externalKey) {
        // Convert PC display text back to internal KeyStroke format
        return switch (externalKey.toLowerCase()) {
            case "menu" -> "ctrl"; // Cross-platform "menu" = Ctrl on Windows/Linux
            case "command" -> "meta"; // Command key maps to meta on PC too
            case "win" -> "meta";
            case "alt" -> "alt";
            case "shift" -> "shift";
            case "ctrl" -> "ctrl";
            case "tab" -> "TAB";
            case "enter" -> "ENTER";
            case "del" -> "DELETE";
            case "space" -> "SPACE";
            case "esc" -> "ESCAPE";
            case "↑" -> "UP";
            case "↓" -> "DOWN";
            case "←" -> "LEFT";
            case "→" -> "RIGHT";
            default -> externalKey.toUpperCase(); // Use as-is for regular keys
        };
    }

    private int computeMenuKey() {
        try {
            // Try to get the platform-specific menu key
            if (java.awt.GraphicsEnvironment.isHeadless()) {
                // In headless environment, return platform-appropriate default
                return switch (platform) {
                    case MACOS -> InputEvent.META_DOWN_MASK;
                    case WINDOWS, LINUX -> InputEvent.CTRL_DOWN_MASK;
                };
            } else {
                return Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
            }
        } catch (Exception e) {
            logger.warn("Failed to get menu shortcut key, using platform default", e);
            // Fall back to platform-appropriate default
            return switch (platform) {
                case MACOS -> InputEvent.META_DOWN_MASK;
                case WINDOWS, LINUX -> InputEvent.CTRL_DOWN_MASK;
            };
        }
    }
}
