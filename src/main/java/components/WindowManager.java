package components;

import info.DefaultPreferencesProvider;
import info.PreferencesProvider;
import info.UserPrefs;
import java.awt.Rectangle;
import java.util.Objects;
import javax.swing.JFrame;

/**
 * Manages window state persistence and restoration.
 *
 * <p>Saves and restores window position, size, maximized state, and split pane divider location.
 */
public class WindowManager {
    private final PreferencesProvider prefs;

    /**
     * Creates a WindowManager with the default preferences provider.
     *
     * <p>Uses the system preferences node associated with the UserPrefs class to persist window
     * state across application sessions.
     */
    public WindowManager() {
        this(new DefaultPreferencesProvider());
    }

    /**
     * Creates a WindowManager with a custom PreferencesProvider.
     *
     * <p>This constructor is primarily used for testing, allowing injection of mock preferences
     * providers.
     *
     * @param prefs the PreferencesProvider for storing and retrieving window state
     */
    public WindowManager(PreferencesProvider prefs) {
        this.prefs = Objects.requireNonNull(prefs);
    }

    /**
     * Restores the window layout from saved preferences.
     *
     * <p>This method restores:
     *
     * <ul>
     *   <li>Window position (x, y coordinates)
     *   <li>Window size (width, height)
     *   <li>Maximized state
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
    public void restoreWindowLayout(JFrame frame, MySplitPane splitPane) {
        Objects.requireNonNull(frame);
        Objects.requireNonNull(splitPane);
        restoreFramePosition(frame);
        restoreDividerLocation(splitPane);
    }

    private void restoreFramePosition(JFrame frame) {
        Objects.requireNonNull(frame);
        frame.setBounds(
                prefs.getInt(UserPrefs.windowXLocation, 0),
                prefs.getInt(UserPrefs.windowYLocation, 0),
                prefs.getInt(UserPrefs.windowWidth, UserPrefs.defaultWindowWidth),
                prefs.getInt(UserPrefs.windowHeight, UserPrefs.defaultWindowHeight));
        // Don't try to maximize on macOS - it doesn't work properly
        // Just let the saved bounds determine the size
    }

    private void restoreDividerLocation(MySplitPane splitPane) {
        Objects.requireNonNull(splitPane);
        int windowHeight = prefs.getInt(UserPrefs.windowHeight, UserPrefs.defaultWindowHeight);
        splitPane.setDividerLocation(prefs.getInt(UserPrefs.dividerLocation, windowHeight / 2));
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
    public void saveWindowLayout(JFrame frame, MySplitPane splitPane) {
        Objects.requireNonNull(frame);
        Objects.requireNonNull(splitPane);
        saveFrameState(frame);
        saveDividerLocation(splitPane);
        prefs.flush();
    }

    private void saveFrameState(JFrame frame) {
        Objects.requireNonNull(frame);
        // On macOS, maximize doesn't set extended state properly
        // Just save the current bounds always
        Rectangle bounds = frame.getBounds();
        prefs.putInt(UserPrefs.windowXLocation, bounds.x);
        prefs.putInt(UserPrefs.windowYLocation, bounds.y);
        prefs.putInt(UserPrefs.windowWidth, bounds.width);
        prefs.putInt(UserPrefs.windowHeight, bounds.height);

        // Don't bother with maximize state on macOS
        prefs.putBoolean(UserPrefs.windowMaximized, false);
    }

    private void saveDividerLocation(MySplitPane splitPane) {
        Objects.requireNonNull(splitPane);
        prefs.putInt(UserPrefs.dividerLocation, splitPane.getDividerLocation());
    }
}
