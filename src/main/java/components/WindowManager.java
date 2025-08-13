package components;

import info.DefaultPreferencesProvider;
import info.PreferencesProvider;
import info.UserPrefs;
import java.awt.Rectangle;
import javax.swing.JFrame;

/** Manages window positioning, sizing, and layout restoration. */
public class WindowManager {
    private final PreferencesProvider prefs;

    /** Creates a WindowManager with the default preferences provider. */
    public WindowManager() {
        this(new DefaultPreferencesProvider());
    }

    /**
     * Creates a WindowManager with the specified preferences provider.
     *
     * @param prefs the preferences provider to use
     */
    public WindowManager(PreferencesProvider prefs) {
        this.prefs = prefs;
    }

    /**
     * Restores window position, size, and split pane divider location from saved preferences.
     *
     * @param frame The frame to restore
     * @param splitPane The split pane to restore divider location for
     */
    public void restoreWindowLayout(JFrame frame, MySplitPane splitPane) {
        restoreFramePosition(frame);
        restoreDividerLocation(splitPane);
    }

    private void restoreFramePosition(JFrame frame) {
        if (prefs.getBoolean(UserPrefs.windowMaximized, UserPrefs.defaultWindowMaximized)) {
            frame.setLocation(0, 0);
            frame.setBounds(0, 0, UserPrefs.defaultWindowWidth, UserPrefs.defaultWindowHeight);
            frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        } else {
            int lastX = prefs.getInt(UserPrefs.windowXLocation, 0);
            int lastY = prefs.getInt(UserPrefs.windowYLocation, 0);
            int lastWidth = prefs.getInt(UserPrefs.windowWidth, UserPrefs.defaultWindowWidth);
            int lastHeight = prefs.getInt(UserPrefs.windowHeight, UserPrefs.defaultWindowHeight);

            frame.setBounds(new Rectangle(lastX, lastY, lastWidth, lastHeight));
        }
    }

    private void restoreDividerLocation(MySplitPane splitPane) {
        int halfway = prefs.getInt(UserPrefs.windowHeight, UserPrefs.defaultWindowHeight) / 2;
        int dividerLocation = prefs.getInt(UserPrefs.dividerLocation, halfway);
        splitPane.setDividerLocation(dividerLocation);
    }
}
