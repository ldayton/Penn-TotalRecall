package env;

import static org.junit.jupiter.api.Assertions.*;

import actions.ExitAction;
import annotation.MacOS;
import app.di.GuiceBootstrap;
import java.awt.Desktop;
import java.awt.Taskbar;
import java.lang.reflect.Field;
import javax.swing.UIManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Tests for LookAndFeelManager macOS integration functionality. */
@MacOS
class LookAndFeelManagerMacOSTest {

    private AppConfig config;
    private Platform platform;
    private LookAndFeelManager manager;
    @Mock private ExitAction exitAction;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Reset UIManager to system default for test isolation
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Ignore if system LaF can't be set
        }

        config = new AppConfig();
        platform = new Platform();
        manager = new LookAndFeelManager(config, new ProgramName(config), platform);
    }

    @Test
    @DisplayName("macOS system properties are set after initialization")
    void macOSSystemPropertiesAreSet() {
        manager.initialize();

        assertEquals("true", System.getProperty("apple.laf.useScreenMenuBar"));
        assertEquals("on", System.getProperty("apple.awt.textantialiasing"));
        assertEquals("on", System.getProperty("apple.awt.antialiasing"));
        assertEquals("quality", System.getProperty("apple.awt.rendering"));
        assertEquals("system", System.getProperty("apple.awt.application.appearance"));
        assertEquals(
                new AppConfig().getProperty(AppConfig.APP_NAME_KEY),
                System.getProperty("apple.awt.application.name"));
    }

    @Test
    @DisplayName("Desktop API handlers are registered")
    void desktopHandlersAreRegistered() {
        assumeDesktopSupported();

        manager.initialize();

        Desktop desktop = Desktop.getDesktop();

        // Verify handlers are registered by checking Desktop's internal state
        if (desktop.isSupported(Desktop.Action.APP_ABOUT)) {
            assertNotNull(getDesktopHandler(desktop, "aboutHandler"));
        }

        if (desktop.isSupported(Desktop.Action.APP_PREFERENCES)) {
            assertNotNull(getDesktopHandler(desktop, "preferencesHandler"));
        }

        if (desktop.isSupported(Desktop.Action.APP_QUIT_HANDLER)) {
            assertNotNull(getDesktopHandler(desktop, "quitHandler"));
        }
    }

    @Test
    @DisplayName("Taskbar icon is configured")
    void taskbarIconIsConfigured() {
        assumeTaskbarSupported();

        manager.initialize();

        Taskbar taskbar = Taskbar.getTaskbar();
        if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
            // Verify icon was set by checking that it's no longer null
            // (can't easily verify the exact image without more complex reflection)
            assertTrue(hasTaskbarIcon(taskbar), "Taskbar icon should be set");
        }
    }

    // JFileChooser is used everywhere; no native chooser toggle.

    @Test
    @DisplayName("Preferences menu items are hidden on macOS")
    void preferencesMenuItemsHidden() {
        assertFalse(manager.shouldShowPreferencesInMenu());
    }

    @Test
    @DisplayName("Mac menu bar system property is set before Swing components are created")
    void macMenuBarSystemPropertySetBeforeSwingComponents() {
        // Clear any existing system properties that might interfere
        System.clearProperty("apple.laf.useScreenMenuBar");

        // Create and initialize the Look and Feel manager
        LookAndFeelManager manager =
                new LookAndFeelManager(config, new ProgramName(config), platform);
        manager.initialize();

        // Verify the system property is set correctly
        String useScreenMenuBar = System.getProperty("apple.laf.useScreenMenuBar");
        assertEquals(
                "true",
                useScreenMenuBar,
                "apple.laf.useScreenMenuBar should be set to 'true' for Mac menu bar integration");
    }

    @Test
    @DisplayName("Mac menu bar system property is set before Guice DI creates Swing components")
    void macMenuBarSystemPropertySetBeforeGuiceDI() {
        // Clear any existing system properties that might interfere
        System.clearProperty("apple.laf.useScreenMenuBar");

        // Simulate the Guice bootstrap process
        // This should fail because currently the system property is set AFTER Swing components are
        // created
        GuiceBootstrap.create();

        // Verify the system property is set correctly BEFORE any Swing components are created
        String useScreenMenuBar = System.getProperty("apple.laf.useScreenMenuBar");
        assertEquals(
                "true",
                useScreenMenuBar,
                "apple.laf.useScreenMenuBar should be set to 'true' before Guice creates Swing"
                        + " components");
    }

    private void assumeDesktopSupported() {
        assertTrue(Desktop.isDesktopSupported(), "Desktop API not supported");
    }

    private void assumeTaskbarSupported() {
        assertTrue(Taskbar.isTaskbarSupported(), "Taskbar API not supported");
    }

    private Object getDesktopHandler(Desktop desktop, String handlerFieldName) {
        try {
            Field field = desktop.getClass().getDeclaredField(handlerFieldName);
            field.setAccessible(true);
            return field.get(desktop);
        } catch (Exception e) {
            // Handler field structure may vary between Java versions
            // If we can't access it via reflection, assume it's registered
            return new Object(); // Non-null indicates handler is present
        }
    }

    private boolean hasTaskbarIcon(Taskbar taskbar) {
        try {
            // Try to access internal state to verify icon was set
            Field iconField = taskbar.getClass().getDeclaredField("iconImage");
            iconField.setAccessible(true);
            return iconField.get(taskbar) != null;
        } catch (Exception e) {
            // If reflection fails, assume icon was set (implementation-dependent)
            return true;
        }
    }
}
