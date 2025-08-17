package components;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import env.PreferencesManager;
import info.UserPrefs;
import java.awt.Rectangle;
import javax.swing.JFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("WindowManager")
class WindowManagerTest {
    @Mock private PreferencesManager mockPrefs;
    @Mock private JFrame mockFrame;
    @Mock private MySplitPane mockSplitPane;

    private WindowManager windowManager;

    @BeforeEach
    void setUp() {
        windowManager = new WindowManager(mockPrefs);
    }

    @Test
    @DisplayName("remembers where you left the window")
    void remembersWindowPosition() {
        // User moves window to specific spot
        when(mockFrame.getBounds()).thenReturn(new Rectangle(100, 50, 1024, 768));
        when(mockSplitPane.getDividerLocation()).thenReturn(400);

        windowManager.saveWindowLayout(mockFrame, mockSplitPane);

        // Window closed and reopened - appears in same spot
        when(mockPrefs.getInt(UserPrefs.windowXLocation, 0)).thenReturn(100);
        when(mockPrefs.getInt(UserPrefs.windowYLocation, 0)).thenReturn(50);
        when(mockPrefs.getInt(UserPrefs.windowWidth, UserPrefs.defaultWindowWidth))
                .thenReturn(1024);
        when(mockPrefs.getInt(UserPrefs.windowHeight, UserPrefs.defaultWindowHeight))
                .thenReturn(768);
        when(mockPrefs.getInt(UserPrefs.dividerLocation, 384)).thenReturn(400);

        windowManager.restoreWindowLayout(mockFrame, mockSplitPane);

        verify(mockFrame).setBounds(100, 50, 1024, 768);
        verify(mockSplitPane).setDividerLocation(400);
    }

    @Test
    @DisplayName("opens at default size on first launch")
    void opensAtDefaultsOnFirstLaunch() {
        // No saved preferences - use defaults
        when(mockPrefs.getInt(anyString(), anyInt()))
                .thenAnswer(invocation -> invocation.getArgument(1));

        windowManager.restoreWindowLayout(mockFrame, mockSplitPane);

        verify(mockFrame)
                .setBounds(0, 0, UserPrefs.defaultWindowWidth, UserPrefs.defaultWindowHeight);
        verify(mockSplitPane).setDividerLocation(UserPrefs.defaultWindowHeight / 2);
    }

    @Test
    @DisplayName("saves immediately when window closes")
    void savesImmediately() {
        when(mockFrame.getBounds()).thenReturn(new Rectangle(0, 0, 800, 600));
        when(mockSplitPane.getDividerLocation()).thenReturn(300);

        windowManager.saveWindowLayout(mockFrame, mockSplitPane);

        verify(mockPrefs).flush();
    }

    @Test
    @DisplayName("rejects null inputs")
    void rejectsNullInputs() {
        assertThrows(NullPointerException.class, () -> new WindowManager(null));
        assertThrows(
                NullPointerException.class,
                () -> windowManager.restoreWindowLayout(null, mockSplitPane));
        assertThrows(
                NullPointerException.class, () -> windowManager.saveWindowLayout(mockFrame, null));
    }
}
