package env;

/**
 * Constants for preference keys used throughout the application.
 *
 * <p>This class centralizes all preference key names to avoid magic strings and ensure consistency
 * across the codebase.
 */
public final class PreferenceKeys {

    // Window management preferences
    public static final String WINDOW_X = "WINDOW_X";
    public static final String WINDOW_Y = "WINDOW_Y";
    public static final String WINDOW_WIDTH = "WINDOW_WIDTH";
    public static final String WINDOW_HEIGHT = "WINDOW_HEIGHT";
    public static final String WINDOW_MAXIMIZED = "WINDOW_MAXIMIZED";
    public static final String DIVIDER_LOCATION = "DIVIDER_LOCATION";
    public static final String FIRST_RUN = "FIRST_RUN";

    // File operation preferences
    public static final String OPEN_LOCATION_PATH = "OPEN_LOCATION_PATH";
    public static final String OPEN_WORDPOOL_PATH = "OPEN_WORDPOOL_PATH";

    // User interface preferences
    public static final String WARN_ON_EXIT = "WARN_ON_EXIT";
    public static final String WARN_FILE_SWITCH = "WARN_FILE_SWITCH";
    public static final String USE_EMACS = "USE_EMACS";

    // Audio processing preferences
    public static final String MIN_BAND_PASS = "MIN_BAND_PASS";
    public static final String MAX_BAND_PASS = "MAX_BAND_PASS";

    // Seek and navigation preferences
    public static final String SMALL_SHIFT = "SMALL_SHIFT";
    public static final String MEDIUM_SHIFT = "MEDIUM_SHIFT";
    public static final String LARGE_SHIFT = "LARGE_SHIFT";

    // Default values
    public static final int DEFAULT_MIN_BAND_PASS = 1000;
    public static final int DEFAULT_MAX_BAND_PASS = 16000;
    public static final boolean DEFAULT_WARN_ON_EXIT = true;
    public static final boolean DEFAULT_WARN_FILE_SWITCH = true;
    public static final boolean DEFAULT_USE_EMACS = false;
    public static final boolean DEFAULT_WINDOW_MAXIMIZED = false;
    public static final int DEFAULT_WINDOW_WIDTH = 1000;
    public static final int DEFAULT_WINDOW_HEIGHT = 500;
    public static final int DEFAULT_SMALL_SHIFT = 5;
    public static final int DEFAULT_MEDIUM_SHIFT = 50;
    public static final int DEFAULT_LARGE_SHIFT = 500;

    /** Prevent instantiation. */
    private PreferenceKeys() {}
}
