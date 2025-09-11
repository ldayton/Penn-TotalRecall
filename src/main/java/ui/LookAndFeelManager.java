package ui;

import core.env.AppConfig;
import core.env.Platform;
import core.env.ProgramName;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.Desktop;
import java.awt.Taskbar;
import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.imageio.ImageIO;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configures platform-specific UI integration (macOS properties, handlers, dock icon). Call
 * initialize() before creating any Swing UI.
 */
@Singleton
public class LookAndFeelManager {
    private static final Logger logger = LoggerFactory.getLogger(LookAndFeelManager.class);

    private final AppConfig appConfig;
    private final ProgramName programName;
    private final Platform platform;

    @Inject
    public LookAndFeelManager(
            @NonNull AppConfig appConfig,
            @NonNull ProgramName programName,
            @NonNull Platform platform) {
        this.appConfig = appConfig;
        this.programName = programName;
        this.platform = platform;
    }

    /** Configures platform properties and enables native integration. */
    public void initialize() {
        if (platform.detect() == Platform.PlatformType.MACOS) {
            macBeforeLookAndFeel();
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
        System.setProperty("apple.awt.application.name", programName.toString());
    }

    /** Configures macOS Desktop API handlers and dock integration. */
    private void macAfterLookAndFeel() {
        Desktop desktop = Desktop.getDesktop();
        // About menu handler
        if (desktop.isSupported(Desktop.Action.APP_ABOUT)) {
            desktop.setAboutHandler(
                    _ -> {
                        var aboutAction =
                                app.swing.SwingApp.getRequiredInjectedInstance(
                                        core.actions.impl.AboutAction.class, "AboutAction");
                        aboutAction.execute();
                    });
        }

        // Preferences menu handler
        if (desktop.isSupported(Desktop.Action.APP_PREFERENCES)) {
            desktop.setPreferencesHandler(
                    _ -> {
                        var preferencesAction =
                                app.swing.SwingApp.getRequiredInjectedInstance(
                                        core.actions.impl.PreferencesAction.class,
                                        "PreferencesAction");
                        preferencesAction.execute();
                    });
        }

        // Quit handler with proper cleanup
        if (desktop.isSupported(Desktop.Action.APP_QUIT_HANDLER)) {
            desktop.setQuitHandler(
                    (_, response) -> {
                        try {
                            // Use the DI-managed ExitAction - fail fast if not available
                            var exitAction =
                                    app.swing.SwingApp.getRequiredInjectedInstance(
                                            core.actions.impl.ExitAction.class, "ExitAction");
                            exitAction.execute();
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
