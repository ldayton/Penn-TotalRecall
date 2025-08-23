package env;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import actions.ExitAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for LookAndFeelManager configuration resolution and platform detection. */
class LookAndFeelManagerTest {

    @Test
    @DisplayName("User configuration overrides default Look and Feel")
    void userConfigOverridesDefault() {
        AppConfig config = mock(AppConfig.class);
        Platform platform = mock(Platform.class);
        ExitAction exitAction = mock(ExitAction.class);
        when(config.getProperty("ui.look_and_feel")).thenReturn("com.example.CustomLaf");

        LookAndFeelManager manager = new LookAndFeelManager(config, platform);

        // Use reflection to access private method
        String lafClass = invokeLookAndFeelClassName(manager);

        assertEquals("com.example.CustomLaf", lafClass);
    }

    @Test
    @DisplayName("Empty user configuration falls back to default")
    void emptyConfigFallsBackToDefault() {
        AppConfig config = mock(AppConfig.class);
        Platform platform = mock(Platform.class);
        ExitAction exitAction = mock(ExitAction.class);
        when(config.getProperty("ui.look_and_feel")).thenReturn("");
        when(config.getProperty("ui.look_and_feel", "com.formdev.flatlaf.FlatLightLaf"))
                .thenReturn("com.formdev.flatlaf.FlatLightLaf");

        LookAndFeelManager manager = new LookAndFeelManager(config, platform);

        String lafClass = invokeLookAndFeelClassName(manager);

        assertEquals("com.formdev.flatlaf.FlatLightLaf", lafClass);
    }

    @Test
    @DisplayName("macOS hides preferences menu items")
    void macOSHidesPreferencesMenu() {
        AppConfig config = mock(AppConfig.class);
        Platform platform = mock(Platform.class);
        ExitAction exitAction = mock(ExitAction.class);
        when(platform.detect()).thenReturn(Platform.PlatformType.MACOS);

        LookAndFeelManager manager = new LookAndFeelManager(config, platform);

        assertFalse(manager.shouldShowPreferencesInMenu());
    }

    @Test
    @DisplayName("Non-macOS platforms show preferences menu items")
    void nonMacOSShowsPreferencesMenu() {
        AppConfig config = mock(AppConfig.class);
        Platform platform = mock(Platform.class);
        ExitAction exitAction = mock(ExitAction.class);
        when(platform.detect()).thenReturn(Platform.PlatformType.WINDOWS);

        LookAndFeelManager manager = new LookAndFeelManager(config, platform);

        assertTrue(manager.shouldShowPreferencesInMenu());
    }

    @Test
    @DisplayName("Icon path selection based on platform")
    void iconPathSelectionByPlatform() {
        AppConfig config = mock(AppConfig.class);
        Platform platform = mock(Platform.class);

        // Test Windows
        when(platform.detect()).thenReturn(Platform.PlatformType.WINDOWS);
        ExitAction exitAction = mock(ExitAction.class);
        LookAndFeelManager windowsManager = new LookAndFeelManager(config, platform);
        assertEquals("/images/headphones48.png", windowsManager.getAppIconPath());

        // Test macOS
        when(platform.detect()).thenReturn(Platform.PlatformType.MACOS);
        LookAndFeelManager macManager = new LookAndFeelManager(config, platform);
        assertEquals("/images/headphones16.png", macManager.getAppIconPath());

        // Test Linux
        when(platform.detect()).thenReturn(Platform.PlatformType.LINUX);
        LookAndFeelManager linuxManager = new LookAndFeelManager(config, platform);
        assertEquals("/images/headphones16.png", linuxManager.getAppIconPath());
    }

    private String invokeLookAndFeelClassName(LookAndFeelManager manager) {
        try {
            var method = LookAndFeelManager.class.getDeclaredMethod("getLookAndFeelClassName");
            method.setAccessible(true);
            return (String) method.invoke(manager);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke getLookAndFeelClassName", e);
        }
    }
}
