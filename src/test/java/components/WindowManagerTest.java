package components;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import info.PreferencesProvider;
import info.UserPrefs;
import java.awt.Rectangle;
import javax.swing.JFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("WindowManager")
class WindowManagerTest {
    @Mock private JFrame mockFrame;
    @Mock private MySplitPane mockSplitPane;
    @Mock private PreferencesProvider mockPrefs;

    private WindowManager windowManager;

    @BeforeEach
    void setUp() {
        windowManager = new WindowManager(mockPrefs);
    }

    @Nested
    @DisplayName("Frame Position Restoration")
    class FramePositionTests {
        @Test
        @DisplayName("should maximize window when previously maximized")
        void shouldMaximizeWindowWhenPreviouslyMaximized() {
            setupMaximizedPreferences();

            windowManager.restoreWindowLayout(mockFrame, mockSplitPane);

            verify(mockFrame).setLocation(0, 0);
            verify(mockFrame)
                    .setBounds(0, 0, UserPrefs.defaultWindowWidth, UserPrefs.defaultWindowHeight);
            verify(mockFrame).setExtendedState(JFrame.MAXIMIZED_BOTH);
        }

        @Test
        @DisplayName("should restore normal window bounds from preferences")
        void shouldRestoreNormalWindowBoundsFromPreferences() {
            setupNormalWindowPreferences(100, 50, 800, 600);

            windowManager.restoreWindowLayout(mockFrame, mockSplitPane);

            verify(mockFrame).setBounds(new Rectangle(100, 50, 800, 600));
            verify(mockFrame, never()).setLocation(anyInt(), anyInt());
            verify(mockFrame, never()).setExtendedState(anyInt());
        }

        @ParameterizedTest
        @DisplayName("should handle various window positions correctly")
        @CsvSource({
            "0, 0, 800, 600", // Top-left corner
            "100, 100, 1024, 768", // Normal position
            "1920, 0, 800, 600", // Secondary monitor
            "50, 50, 1280, 720" // Different size
        })
        void shouldHandleVariousWindowPositions(int x, int y, int width, int height) {
            setupNormalWindowPreferences(x, y, width, height);

            windowManager.restoreWindowLayout(mockFrame, mockSplitPane);

            verify(mockFrame).setBounds(new Rectangle(x, y, width, height));
        }
    }

    @Nested
    @DisplayName("Divider Location Restoration")
    class DividerLocationTests {
        @Test
        @DisplayName("should set divider to half window height by default")
        void shouldSetDividerToHalfWindowHeightByDefault() {
            setupNormalWindowPreferences(0, 0, 800, 600);
            // Divider location defaults to halfway (300 for height 600)
            when(mockPrefs.getInt(UserPrefs.dividerLocation, 300)).thenReturn(300);

            windowManager.restoreWindowLayout(mockFrame, mockSplitPane);

            verify(mockSplitPane).setDividerLocation(300);
        }

        @Test
        @DisplayName("should restore saved divider location from preferences")
        void shouldRestoreSavedDividerLocationFromPreferences() {
            setupNormalWindowPreferences(0, 0, 800, 800);
            // Custom divider location (250) different from default (400)
            when(mockPrefs.getInt(UserPrefs.dividerLocation, 400)).thenReturn(250);

            windowManager.restoreWindowLayout(mockFrame, mockSplitPane);

            verify(mockSplitPane).setDividerLocation(250);
        }

        @ParameterizedTest
        @DisplayName("should handle various divider positions")
        @ValueSource(ints = {100, 200, 300, 450, 500})
        void shouldHandleVariousDividerPositions(int dividerLocation) {
            setupNormalWindowPreferences(0, 0, 800, 600);
            when(mockPrefs.getInt(UserPrefs.dividerLocation, 300)).thenReturn(dividerLocation);

            windowManager.restoreWindowLayout(mockFrame, mockSplitPane);

            verify(mockSplitPane).setDividerLocation(dividerLocation);
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {
        @Test
        @DisplayName("should restore both window position and divider for maximized window")
        void shouldRestoreMaximizedWindowWithSavedDividerLocation() {
            setupMaximizedPreferences();
            when(mockPrefs.getInt(UserPrefs.dividerLocation, 300)).thenReturn(250);

            windowManager.restoreWindowLayout(mockFrame, mockSplitPane);

            // Verify frame operations for maximized state
            verify(mockFrame).setLocation(0, 0);
            verify(mockFrame)
                    .setBounds(0, 0, UserPrefs.defaultWindowWidth, UserPrefs.defaultWindowHeight);
            verify(mockFrame).setExtendedState(JFrame.MAXIMIZED_BOTH);
            // Verify split pane operation
            verify(mockSplitPane).setDividerLocation(250);
        }

        @Test
        @DisplayName("should restore both window position and divider for normal window")
        void shouldRestoreNormalWindowWithSavedDividerLocation() {
            setupNormalWindowPreferences(150, 75, 1024, 768);
            when(mockPrefs.getInt(UserPrefs.dividerLocation, 384)).thenReturn(400);

            windowManager.restoreWindowLayout(mockFrame, mockSplitPane);

            verify(mockFrame).setBounds(new Rectangle(150, 75, 1024, 768));
            verify(mockSplitPane).setDividerLocation(400);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {
        @Test
        @DisplayName("should handle negative window coordinates")
        void shouldHandleNegativeWindowCoordinates() {
            setupNormalWindowPreferences(-100, -50, 800, 600);

            windowManager.restoreWindowLayout(mockFrame, mockSplitPane);

            // Should still set bounds even with negative coordinates (off-screen)
            verify(mockFrame).setBounds(new Rectangle(-100, -50, 800, 600));
        }

        @Test
        @DisplayName("should handle extremely large window dimensions")
        void shouldHandleExtremelyLargeWindowDimensions() {
            setupNormalWindowPreferences(0, 0, 5000, 3000);
            when(mockPrefs.getInt(UserPrefs.dividerLocation, 1500)).thenReturn(1500);

            windowManager.restoreWindowLayout(mockFrame, mockSplitPane);

            verify(mockFrame).setBounds(new Rectangle(0, 0, 5000, 3000));
            verify(mockSplitPane).setDividerLocation(1500);
        }

        @Test
        @DisplayName("should handle zero divider location")
        void shouldHandleZeroDividerLocation() {
            setupNormalWindowPreferences(0, 0, 800, 600);
            when(mockPrefs.getInt(UserPrefs.dividerLocation, 300)).thenReturn(0);

            windowManager.restoreWindowLayout(mockFrame, mockSplitPane);

            verify(mockSplitPane).setDividerLocation(0);
        }

        @Test
        @DisplayName("should handle divider location beyond window bounds")
        void shouldHandleDividerLocationBeyondWindowBounds() {
            setupNormalWindowPreferences(0, 0, 800, 600);
            when(mockPrefs.getInt(UserPrefs.dividerLocation, 300)).thenReturn(1000);

            windowManager.restoreWindowLayout(mockFrame, mockSplitPane);

            // Should still set the divider location even if beyond bounds
            // The split pane will handle clamping internally
            verify(mockSplitPane).setDividerLocation(1000);
        }
    }

    // Helper methods
    private void setupMaximizedPreferences() {
        when(mockPrefs.getBoolean(UserPrefs.windowMaximized, UserPrefs.defaultWindowMaximized))
                .thenReturn(true);
        when(mockPrefs.getInt(UserPrefs.windowHeight, UserPrefs.defaultWindowHeight))
                .thenReturn(600);
    }

    private void setupNormalWindowPreferences(int x, int y, int width, int height) {
        when(mockPrefs.getBoolean(UserPrefs.windowMaximized, UserPrefs.defaultWindowMaximized))
                .thenReturn(false);
        when(mockPrefs.getInt(UserPrefs.windowXLocation, 0)).thenReturn(x);
        when(mockPrefs.getInt(UserPrefs.windowYLocation, 0)).thenReturn(y);
        when(mockPrefs.getInt(UserPrefs.windowWidth, UserPrefs.defaultWindowWidth))
                .thenReturn(width);
        when(mockPrefs.getInt(UserPrefs.windowHeight, UserPrefs.defaultWindowHeight))
                .thenReturn(height);
    }
}
