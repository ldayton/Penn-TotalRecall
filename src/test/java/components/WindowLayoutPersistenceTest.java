package components;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import env.PreferenceKeys;
import java.awt.Rectangle;
import javax.swing.JFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import state.PreferencesManager;

@ExtendWith(MockitoExtension.class)
@DisplayName("WindowLayoutPersistence")
class WindowLayoutPersistenceTest {
    @Mock private PreferencesManager mockPrefs;
    @Mock private JFrame mockFrame;
    @Mock private ContentSplitPane mockSplitPane;

    private WindowLayoutPersistence windowManager;

    @BeforeEach
    void setUp() {
        windowManager = new WindowLayoutPersistence(mockPrefs);
    }

    @Test
    @DisplayName("remembers where you left the window")
    void remembersWindowPosition() {
        // User moves window to specific spot
        when(mockFrame.getBounds()).thenReturn(new Rectangle(100, 50, 1024, 768));
        when(mockSplitPane.getDividerLocation()).thenReturn(400);

        windowManager.saveWindowLayout(mockFrame, mockSplitPane);

        // Window closed and reopened - appears in same spot
        when(mockPrefs.hasKey(PreferenceKeys.WINDOW_X)).thenReturn(true);
        when(mockPrefs.hasKey(PreferenceKeys.WINDOW_Y)).thenReturn(true);
        when(mockPrefs.hasKey(PreferenceKeys.WINDOW_WIDTH)).thenReturn(true);
        when(mockPrefs.hasKey(PreferenceKeys.WINDOW_HEIGHT)).thenReturn(true);
        when(mockPrefs.hasKey(PreferenceKeys.DIVIDER_LOCATION)).thenReturn(true);
        when(mockPrefs.getInt(PreferenceKeys.WINDOW_X, 0)).thenReturn(100);
        when(mockPrefs.getInt(PreferenceKeys.WINDOW_Y, 0)).thenReturn(50);
        when(mockPrefs.getInt(PreferenceKeys.WINDOW_WIDTH, PreferenceKeys.DEFAULT_WINDOW_WIDTH))
                .thenReturn(1024);
        when(mockPrefs.getInt(PreferenceKeys.WINDOW_HEIGHT, PreferenceKeys.DEFAULT_WINDOW_HEIGHT))
                .thenReturn(768);
        when(mockPrefs.getInt(PreferenceKeys.DIVIDER_LOCATION, 384)).thenReturn(400);

        windowManager.restoreWindowLayout(mockFrame, mockSplitPane);

        verify(mockFrame).setBounds(100, 50, 1024, 768);
        verify(mockSplitPane).setDividerLocation(400);
    }

    @Test
    @DisplayName("opens at default size on first launch")
    void opensAtDefaultsOnFirstLaunch() {
        // No saved preferences - use defaults
        when(mockPrefs.hasKey(anyString())).thenReturn(false);

        windowManager.restoreWindowLayout(mockFrame, mockSplitPane);

        verify(mockFrame)
                .setSize(PreferenceKeys.DEFAULT_WINDOW_WIDTH, PreferenceKeys.DEFAULT_WINDOW_HEIGHT);
        verify(mockFrame).setLocationRelativeTo(null);
        verify(mockSplitPane).setDividerLocation(PreferenceKeys.DEFAULT_WINDOW_HEIGHT / 2);
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
        assertThrows(NullPointerException.class, () -> new WindowLayoutPersistence(null));
        assertThrows(
                NullPointerException.class,
                () -> windowManager.restoreWindowLayout(null, mockSplitPane));
        assertThrows(
                NullPointerException.class, () -> windowManager.saveWindowLayout(mockFrame, null));
    }
}
