package ui;

import core.env.PreferenceKeys;
import jakarta.inject.Inject;
import java.awt.Rectangle;
import javax.swing.JFrame;
import lombok.NonNull;
import ui.preferences.PreferencesManager;

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
        boolean haveAllBounds =
                prefs.hasKey(PreferenceKeys.WINDOW_X)
                        && prefs.hasKey(PreferenceKeys.WINDOW_Y)
                        && prefs.hasKey(PreferenceKeys.WINDOW_WIDTH)
                        && prefs.hasKey(PreferenceKeys.WINDOW_HEIGHT);

        if (haveAllBounds) {
            frame.setBounds(
                    prefs.getInt(PreferenceKeys.WINDOW_X, 0),
                    prefs.getInt(PreferenceKeys.WINDOW_Y, 0),
                    prefs.getInt(PreferenceKeys.WINDOW_WIDTH, PreferenceKeys.DEFAULT_WINDOW_WIDTH),
                    prefs.getInt(
                            PreferenceKeys.WINDOW_HEIGHT, PreferenceKeys.DEFAULT_WINDOW_HEIGHT));
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
                prefs.hasKey(PreferenceKeys.WINDOW_HEIGHT)
                        ? prefs.getInt(
                                PreferenceKeys.WINDOW_HEIGHT, PreferenceKeys.DEFAULT_WINDOW_HEIGHT)
                        : PreferenceKeys.DEFAULT_WINDOW_HEIGHT;
        int defaultDivider = windowHeight / 2;
        int divider =
                prefs.hasKey(PreferenceKeys.DIVIDER_LOCATION)
                        ? prefs.getInt(PreferenceKeys.DIVIDER_LOCATION, defaultDivider)
                        : defaultDivider;
        splitPane.setDividerLocation(divider);
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
