package env;

import behaviors.singleact.AboutAction;
import behaviors.singleact.ExitAction;
import behaviors.singleact.PreferencesAction;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.Desktop;
import java.awt.Taskbar;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.swing.UIManager;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configures Look and Feel and platform-specific UI integration. Call initialize() before creating
 * any Swing components.
 */
@Singleton
public class LookAndFeelManager {
    private static final Logger logger = LoggerFactory.getLogger(LookAndFeelManager.class);

    private final AppConfig appConfig;
    private final Platform platform;
    private final ExitAction exitAction;

    @Inject
    public LookAndFeelManager(
            @NonNull AppConfig appConfig,
            @NonNull Platform platform,
            @NonNull ExitAction exitAction) {
        this.appConfig = appConfig;
        this.platform = platform;
        this.exitAction = exitAction;
    }

    /** Configures platform properties, sets Look and Feel, and enables native integration. */
    public void initialize() {
        if (platform.detect() == Platform.PlatformType.MACOS) {
            macBeforeLookAndFeel();
        }

        String lafClass = getLookAndFeelClassName();
        try {
            UIManager.setLookAndFeel(lafClass);
            logger.debug("Set Look and Feel: {}", lafClass);
        } catch (Exception e) {
            logger.error("Failed to set Look and Feel: {}", lafClass, e);
        }

        if (platform.detect() == Platform.PlatformType.MACOS) {
            macAfterLookAndFeel();
        }
    }

    /** Sets macOS system properties for native rendering and menu bar. */
    private void macBeforeLookAndFeel() {
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("apple.awt.textantialiasing", "on");
        System.setProperty("apple.awt.antialiasing", "on");
        System.setProperty("apple.awt.rendering", "quality");
        System.setProperty("apple.awt.application.appearance", "system");
        System.setProperty("apple.awt.application.name", "Penn TotalRecall");
    }

    /** Returns configured Look and Feel class name, defaulting to FlatLaf. */
    private String getLookAndFeelClassName() {
        // Check user configuration first
        String userLaf = appConfig.getProperty("ui.look_and_feel");
        if (userLaf != null && !userLaf.trim().isEmpty()) {
            return userLaf;
        }
        return appConfig.getProperty("ui.look_and_feel", "com.formdev.flatlaf.FlatLightLaf");
    }

    /** Configures macOS Desktop API handlers and dock integration. */
    private void macAfterLookAndFeel() {
        Desktop desktop = Desktop.getDesktop();
        // About menu handler
        if (desktop.isSupported(Desktop.Action.APP_ABOUT)) {
            desktop.setAboutHandler(
                    _ -> {
                        var aboutAction = new AboutAction();
                        var actionEvent =
                                new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "about");
                        aboutAction.actionPerformed(actionEvent);
                    });
        }

        // Preferences menu handler
        if (desktop.isSupported(Desktop.Action.APP_PREFERENCES)) {
            desktop.setPreferencesHandler(
                    _ -> {
                        var preferencesAction = new PreferencesAction();
                        var actionEvent =
                                new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "preferences");
                        preferencesAction.actionPerformed(actionEvent);
                    });
        }

        // Quit handler with proper cleanup
        if (desktop.isSupported(Desktop.Action.APP_QUIT_HANDLER)) {
            desktop.setQuitHandler(
                    (_, response) -> {
                        try {
                            var actionEvent =
                                    new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "quit");
                            exitAction.actionPerformed(actionEvent);
                            response.performQuit();
                        } catch (Exception ex) {
                            logger.error("Error during application quit", ex);
                            response.cancelQuit();
                        }
                    });
        }

        Taskbar taskbar = Taskbar.getTaskbar();

        // Set custom dock icon
        if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
            try {
                var iconStream =
                        LookAndFeelManager.class.getResourceAsStream("/images/headphones128.png");
                if (iconStream != null) {
                    BufferedImage dockIcon = ImageIO.read(iconStream);
                    if (dockIcon != null) {
                        taskbar.setIconImage(dockIcon);
                    }
                }
            } catch (IOException e) {
                logger.warn("Failed to load dock icon: " + e.getMessage());
            }
        }
    }

    /** Returns false on macOS where system menu bar handles these items. */
    public boolean shouldShowPreferencesInMenu() {
        return platform.detect() != Platform.PlatformType.MACOS;
    }

    /**
     * Returns true on macOS for native file choosers, configurable via ui.use_native_file_choosers.
     */
    public boolean shouldUseAWTFileChoosers() {
        boolean defaultValue = platform.detect() == Platform.PlatformType.MACOS;
        return appConfig.getBooleanProperty("ui.use_native_file_choosers", defaultValue);
    }

    /**
     * Returns platform-appropriate preferences menu text, configurable via
     * ui.preferences_menu_title.
     */
    public String getPreferencesString() {
        return appConfig.getProperty("ui.preferences_menu_title", "Options");
    }

    /** Returns platform-specific application icon path. */
    public String getAppIconPath() {
        return switch (platform.detect()) {
            case WINDOWS -> "/images/headphones48.png"; // Modern Windows taskbar
            case MACOS, LINUX -> "/images/headphones16.png";
        };
    }
}
