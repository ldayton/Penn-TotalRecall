package env;

import components.MacOSIntegration;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javax.swing.UIManager;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages Look and Feel initialization and platform-specific UI configuration.
 *
 * <p>Handles:
 *
 * <ul>
 *   <li>Look and Feel selection and initialization
 *   <li>Platform-specific system property configuration
 *   <li>Native OS integration setup
 *   <li>Fallback handling for unsupported Look and Feels
 * </ul>
 *
 * <p>This class is injectable and designed to be called early in application startup, before
 * creating any Swing components.
 */
@Singleton
public class LookAndFeelManager {
    private static final Logger logger = LoggerFactory.getLogger(LookAndFeelManager.class);

    private final AppConfig appConfig;
    private final Platform platform;

    @Inject
    public LookAndFeelManager(@NonNull AppConfig appConfig, @NonNull Platform platform) {
        this.appConfig = appConfig;
        this.platform = platform;
    }

    /**
     * Initializes the Look and Feel based on platform and user configuration. Call this early in
     * application startup, before creating any Swing components.
     */
    public void initialize() {
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

    /** Configures platform-specific system properties for optimal rendering and native behavior. */
    private void configurePlatformProperties() {
        if (platform.detect() == Platform.PlatformType.MACOS) {
            // Configure macOS-specific system properties for optimal rendering
            System.setProperty("apple.laf.useScreenMenuBar", "true");
            System.setProperty("apple.awt.textantialiasing", "on");
            System.setProperty("apple.awt.antialiasing", "on");
            System.setProperty("apple.awt.rendering", "quality");
            System.setProperty("apple.awt.application.appearance", "system");
            System.setProperty("apple.awt.application.name", "Penn TotalRecall");
        }
    }

    /**
     * Determines the Look and Feel class name based on user configuration and platform defaults.
     *
     * @return the fully qualified Look and Feel class name
     */
    private String getLookAndFeelClassName() {
        // Check user configuration first
        String userLaf = appConfig.getProperty("ui.look_and_feel");
        if (userLaf != null && !userLaf.trim().isEmpty()) {
            return userLaf;
        }

        // Fall back to platform default
        return appConfig.getProperty("ui.look_and_feel", "com.formdev.flatlaf.FlatLightLaf");
    }

    /** Configures native OS integration features where available. */
    private void configureNativeIntegration() {
        if (platform.detect() == Platform.PlatformType.MACOS) {
            try {
                MacOSIntegration.integrateWithMacOS();
                logger.debug("macOS integration configured");
            } catch (Exception e) {
                logger.warn("Failed to configure macOS integration", e);
            }
        }
    }

    /**
     * Whether this platform should show Preferences/About in application menus. On Mac, these are
     * handled by the system menu bar.
     *
     * @return true if preferences should be shown in menus, false otherwise
     */
    public boolean shouldShowPreferencesInMenu() {
        return platform.detect() != Platform.PlatformType.MACOS;
    }

    /**
     * Whether this platform should use AWT file choosers instead of Swing ones. macOS generally
     * provides better native file choosers through AWT.
     *
     * @return true if AWT file choosers should be used, false for Swing
     */
    public boolean shouldUseAWTFileChoosers() {
        boolean defaultValue = platform.detect() == Platform.PlatformType.MACOS;
        return appConfig.getBooleanProperty("ui.use_native_file_choosers", defaultValue);
    }

    /**
     * Gets the platform-appropriate preferences menu text.
     *
     * @return "Preferences" on macOS/Linux, "Options" on Windows
     */
    public String getPreferencesString() {
        String defaultValue =
                switch (platform.detect()) {
                    case MACOS, LINUX -> "Preferences";
                    case WINDOWS -> "Options";
                };
        return appConfig.getProperty("ui.preferences_menu_title", defaultValue);
    }

    /**
     * Gets the appropriate application icon path for the platform. Modern platforms support larger
     * icons for better display quality.
     *
     * @return path to the platform-appropriate icon resource
     */
    public String getAppIconPath() {
        return switch (platform.detect()) {
            case WINDOWS -> "/images/headphones48.png"; // Modern Windows taskbar
            case MACOS, LINUX -> "/images/headphones16.png";
        };
    }
}
