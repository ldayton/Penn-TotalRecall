package env;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import core.actions.ExitAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for LookAndFeelManager configuration resolution and platform detection. */
class LookAndFeelManagerTest {

    @Test
    @DisplayName("macOS hides preferences menu items")
    void macOSHidesPreferencesMenu() {
        AppConfig config = mock(AppConfig.class);
        Platform platform = mock(Platform.class);
        mock(ExitAction.class);
        when(platform.detect()).thenReturn(Platform.PlatformType.MACOS);

        LookAndFeelManager manager =
                new LookAndFeelManager(config, mock(ProgramName.class), platform);

        assertFalse(manager.shouldShowPreferencesInMenu());
    }

    @Test
    @DisplayName("Non-macOS platforms show preferences menu items")
    void nonMacOSShowsPreferencesMenu() {
        AppConfig config = mock(AppConfig.class);
        Platform platform = mock(Platform.class);
        mock(ExitAction.class);
        when(platform.detect()).thenReturn(Platform.PlatformType.WINDOWS);

        LookAndFeelManager manager =
                new LookAndFeelManager(config, mock(ProgramName.class), platform);

        assertTrue(manager.shouldShowPreferencesInMenu());
    }

    @Test
    @DisplayName("Icon path selection based on platform")
    void iconPathSelectionByPlatform() {
        AppConfig config = mock(AppConfig.class);
        Platform platform = mock(Platform.class);

        // Test Windows
        when(platform.detect()).thenReturn(Platform.PlatformType.WINDOWS);
        mock(ExitAction.class);
        LookAndFeelManager windowsManager =
                new LookAndFeelManager(config, mock(ProgramName.class), platform);
        assertEquals("/images/headphones48.png", windowsManager.getAppIconPath());

        // Test macOS
        when(platform.detect()).thenReturn(Platform.PlatformType.MACOS);
        LookAndFeelManager macManager =
                new LookAndFeelManager(config, mock(ProgramName.class), platform);
        assertEquals("/images/headphones16.png", macManager.getAppIconPath());

        // Test Linux
        when(platform.detect()).thenReturn(Platform.PlatformType.LINUX);
        LookAndFeelManager linuxManager =
                new LookAndFeelManager(config, mock(ProgramName.class), platform);
        assertEquals("/images/headphones16.png", linuxManager.getAppIconPath());
    }

    // Removed tests and reflection helper for getLookAndFeelClassName; LAF set in bootstrap.
}
