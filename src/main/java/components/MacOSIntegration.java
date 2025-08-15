package components;

import behaviors.singleact.ExitAction;
import behaviors.singleact.PreferencesAction;
import info.SysInfo;
import java.awt.Desktop;
import java.awt.Taskbar;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.GiveMessage;

/**
 * Modern macOS integration using Java 21 Desktop and Taskbar APIs.
 *
 * <p>Provides native macOS experience with menu bar integration, dock customization, and system
 * event handling. Requires Java 9+ and assumes macOS runtime.
 */
public final class MacOSIntegration {
    private static final Logger logger = LoggerFactory.getLogger(MacOSIntegration.class);

    private static boolean initialized = false;

    /**
     * Configures macOS-specific UI integration and system properties.
     *
     * @return true if integration was successful
     * @throws UnsupportedOperationException if not running on macOS or Desktop not supported
     */
    public static boolean integrateWithMacOS() {
        if (initialized) {
            return true;
        }

        // Verify we're on macOS with Desktop support
        if (!Desktop.isDesktopSupported()) {
            throw new UnsupportedOperationException("Desktop integration not supported");
        }

        // Configure macOS system properties for optimal rendering
        configureMacOSProperties();

        // Set up Desktop API integrations
        Desktop desktop = Desktop.getDesktop();
        configureDesktopHandlers(desktop);

        // Configure Taskbar/Dock integration
        configureTaskbarIntegration();

        initialized = true;
        return true;
    }

    /** Configures macOS-specific system properties for native look and feel. */
    private static void configureMacOSProperties() {
        // Move menu bar to top of screen (classic macOS behavior)
        System.setProperty("apple.laf.useScreenMenuBar", "true");

        // Enable high-quality text rendering
        System.setProperty("apple.awt.textantialiasing", "on");
        System.setProperty("apple.awt.antialiasing", "on");
        System.setProperty("apple.awt.rendering", "quality");

        // Modern macOS appearance integration
        System.setProperty("apple.awt.application.appearance", "system");
        System.setProperty("apple.awt.application.name", "Penn TotalRecall");
    }

    /** Configures Desktop API event handlers for About, Preferences, and Quit. */
    private static void configureDesktopHandlers(Desktop desktop) {
        // About menu handler
        if (desktop.isSupported(Desktop.Action.APP_ABOUT)) {
            desktop.setAboutHandler(
                    e -> {
                        GiveMessage.infoMessage(SysInfo.sys.aboutMessage);
                    });
        }

        // Preferences menu handler
        if (desktop.isSupported(Desktop.Action.APP_PREFERENCES)) {
            desktop.setPreferencesHandler(
                    e -> {
                        var preferencesAction = new PreferencesAction();
                        var actionEvent =
                                new ActionEvent(
                                        MyFrame.getInstance(),
                                        ActionEvent.ACTION_PERFORMED,
                                        "preferences");
                        preferencesAction.actionPerformed(actionEvent);
                    });
        }

        // Quit handler with proper cleanup
        if (desktop.isSupported(Desktop.Action.APP_QUIT_HANDLER)) {
            desktop.setQuitHandler(
                    (e, response) -> {
                        try {
                            var exitAction = new ExitAction();
                            var actionEvent =
                                    new ActionEvent(
                                            MyFrame.getInstance(),
                                            ActionEvent.ACTION_PERFORMED,
                                            "quit");
                            exitAction.actionPerformed(actionEvent);
                            response.performQuit();
                        } catch (Exception ex) {
                            logger.error("Error during application quit", ex);
                            response.cancelQuit();
                        }
                    });
        }
    }

    /** Configures Taskbar/Dock integration including custom icon and menu. */
    private static void configureTaskbarIntegration() {
        if (!Taskbar.isTaskbarSupported()) {
            return;
        }

        Taskbar taskbar = Taskbar.getTaskbar();

        // Set custom dock icon
        if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
            try {
                BufferedImage dockIcon =
                        ImageIO.read(
                                MacOSIntegration.class.getResourceAsStream(
                                        "/images/headphones128.png"));
                if (dockIcon != null) {
                    taskbar.setIconImage(dockIcon);
                }
            } catch (IOException e) {
                logger.warn("Failed to load dock icon: " + e.getMessage());
            }
        }

        // Set window icon badge for audio recording status (future enhancement)
        if (taskbar.isSupported(Taskbar.Feature.ICON_BADGE_TEXT)) {
            // Could be used to show recording status, etc.
            // taskbar.setIconBadge("●");
        }

        // Progress indication support for long operations
        if (taskbar.isSupported(Taskbar.Feature.PROGRESS_VALUE)) {
            // Could be used during file processing
            // taskbar.setProgressValue(50);
        }
    }

    /**
     * Checks if the current system supports macOS integration features.
     *
     * @return true if running on macOS with full Desktop API support
     */
    public static boolean isIntegrationSupported() {
        return Desktop.isDesktopSupported()
                && Taskbar.isTaskbarSupported()
                && System.getProperty("os.name", "")
                        .toLowerCase(java.util.Locale.ROOT)
                        .contains("mac");
    }

    /**
     * Returns integration status for debugging.
     *
     * @return human-readable status string
     */
    public static String getIntegrationStatus() {
        if (!isIntegrationSupported()) {
            return "Not supported (not macOS or missing Desktop API)";
        }

        if (!initialized) {
            return "Supported but not initialized";
        }

        Desktop desktop = Desktop.getDesktop();
        Taskbar taskbar = Taskbar.getTaskbar();

        return String.format(
                "Integrated (About: %s, Prefs: %s, Quit: %s, Taskbar: %s)",
                desktop.isSupported(Desktop.Action.APP_ABOUT),
                desktop.isSupported(Desktop.Action.APP_PREFERENCES),
                desktop.isSupported(Desktop.Action.APP_QUIT_HANDLER),
                taskbar.isSupported(Taskbar.Feature.ICON_IMAGE));
    }

    private MacOSIntegration() {
        // Utility class - prevent instantiation
    }
}
