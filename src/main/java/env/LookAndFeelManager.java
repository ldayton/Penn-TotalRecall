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

    private final Environment environment;
    private final AppConfig appConfig;

    @Inject
    public LookAndFeelManager(@NonNull Environment environment, @NonNull AppConfig appConfig) {
        this.environment = environment;
        this.appConfig = appConfig;
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
        if (environment.getPlatform() == Platform.MACOS) {
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
        if (environment.getPlatform() == Platform.MACOS) {
            try {
                MacOSIntegration.integrateWithMacOS();
                logger.debug("macOS integration configured");
            } catch (Exception e) {
                logger.warn("Failed to configure macOS integration", e);
            }
        }
    }
}
