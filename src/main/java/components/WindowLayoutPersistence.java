package components;

import env.PreferenceKeys;
import jakarta.inject.Inject;
import java.awt.Rectangle;
import javax.swing.JFrame;
import lombok.NonNull;
import state.PreferencesManager;

/**
 * Manages window state persistence and restoration.
 *
 * <p>Saves and restores window position, size, and split pane divider location.
 */
public class WindowLayoutPersistence {
    private final PreferencesManager prefs;

    /**
     * Creates a WindowLayoutPersistence with injected PreferencesManager.
     *
     * @param prefs the PreferencesManager for storing and retrieving window state
     */
    @Inject
    public WindowLayoutPersistence(@NonNull PreferencesManager prefs) {
        this.prefs = prefs;
    }

    /**
     * Restores the window layout from saved preferences.
     *
     * <p>This method restores:
     *
     * <ul>
     *   <li>Window position (x, y coordinates)
     *   <li>Window size (width, height)
     *   <li>Split pane divider location
     * </ul>
     *
     * <p>If no saved preferences exist, defaults are used:
     *
     * <ul>
     *   <li>Window position: (0, 0)
     *   <li>Window size: application default dimensions
     *   <li>Maximized: false
     *   <li>Divider: centered based on window height
     * </ul>
     *
     * @param frame the application frame to restore
     * @param splitPane the split pane whose divider location should be restored
     */
    public void restoreWindowLayout(@NonNull JFrame frame, @NonNull ContentSplitPane splitPane) {
        restoreFramePosition(frame);
        restoreDividerLocation(splitPane);
    }

    private void restoreFramePosition(@NonNull JFrame frame) {
        // Detect presence of saved bounds via sentinels (no separate first-run flag)
        int x = prefs.getInt(PreferenceKeys.WINDOW_X, Integer.MIN_VALUE);
        int y = prefs.getInt(PreferenceKeys.WINDOW_Y, Integer.MIN_VALUE);
        int w = prefs.getInt(PreferenceKeys.WINDOW_WIDTH, Integer.MIN_VALUE);
        int h = prefs.getInt(PreferenceKeys.WINDOW_HEIGHT, Integer.MIN_VALUE);

        boolean hasSavedBounds =
                x != Integer.MIN_VALUE
                        && y != Integer.MIN_VALUE
                        && w != Integer.MIN_VALUE
                        && h != Integer.MIN_VALUE;

        if (hasSavedBounds) {
            frame.setBounds(x, y, w, h);
        } else {
            // No prior window state: use defaults and center on screen
            frame.setSize(
                    PreferenceKeys.DEFAULT_WINDOW_WIDTH, PreferenceKeys.DEFAULT_WINDOW_HEIGHT);
            frame.setLocationRelativeTo(null);
        }
        // Don't try to maximize on macOS - it doesn't work properly
        // Just let the saved bounds determine the size
    }

    private void restoreDividerLocation(@NonNull ContentSplitPane splitPane) {
        int windowHeight =
                prefs.getInt(PreferenceKeys.WINDOW_HEIGHT, PreferenceKeys.DEFAULT_WINDOW_HEIGHT);
        splitPane.setDividerLocation(
                prefs.getInt(PreferenceKeys.DIVIDER_LOCATION, windowHeight / 2));
    }

    /**
     * Saves the current window layout to preferences.
     *
     * <p>Persists window position and size (if not maximized), maximized state, and split pane
     * divider location.
     *
     * @param frame the application frame to save state from
     * @param splitPane the split pane whose divider location should be saved
     */
    public void saveWindowLayout(@NonNull JFrame frame, @NonNull ContentSplitPane splitPane) {
        saveFrameState(frame);
        saveDividerLocation(splitPane);
        prefs.flush();
    }

    private void saveFrameState(@NonNull JFrame frame) {
        // On macOS, maximize doesn't set extended state properly
        // Just save the current bounds always
        Rectangle bounds = frame.getBounds();
        prefs.putInt(PreferenceKeys.WINDOW_X, bounds.x);
        prefs.putInt(PreferenceKeys.WINDOW_Y, bounds.y);
        prefs.putInt(PreferenceKeys.WINDOW_WIDTH, bounds.width);
        prefs.putInt(PreferenceKeys.WINDOW_HEIGHT, bounds.height);

        // Don't persist maximize state (platform behavior is inconsistent)
    }

    private void saveDividerLocation(@NonNull ContentSplitPane splitPane) {
        prefs.putInt(PreferenceKeys.DIVIDER_LOCATION, splitPane.getDividerLocation());
    }
}
