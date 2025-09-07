package ui.preferences;

import com.google.common.annotations.VisibleForTesting;
import core.env.UserHomeProvider;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.File;
import java.util.prefs.Preferences;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Generic preferences management for all user preferences. */
@Singleton
public class PreferencesManager {
    private static final Logger logger = LoggerFactory.getLogger(PreferencesManager.class);

    private static final String APP_PREFERENCES_NODE = "/edu/upenn/psych/memory/penntotalrecall";

    private final Preferences prefs;
    private final UserHomeProvider userManager;

    @Inject
    public PreferencesManager(@NonNull UserHomeProvider userManager) {
        this(userManager, System.getProperty("prefs.namespace", APP_PREFERENCES_NODE));
    }

    /** Test constructor allowing custom namespace for isolation. */
    @VisibleForTesting
    public PreferencesManager(@NonNull UserHomeProvider userManager, @NonNull String namespace) {
        this.userManager = userManager;
        this.prefs = Preferences.userRoot().node(namespace);
    }

    /** Gets string preference, logs if using default. */
    public String getString(@NonNull String key, @NonNull String defaultValue) {
        String value = prefs.get(key, null);
        if (value == null) {
            logger.warn("Preference '{}' not found, using default value: {}", key, defaultValue);
            return defaultValue;
        }
        return value;
    }

    /** Sets string preference. */
    public void putString(@NonNull String key, @NonNull String value) {
        prefs.put(key, value);
    }

    /** Gets int preference, logs if using default. */
    public int getInt(@NonNull String key, int defaultValue) {
        String stored = prefs.get(key, null);
        if (stored == null) {
            logger.warn("Preference '{}' not found, using default value: {}", key, defaultValue);
            return defaultValue;
        }

        try {
            return Integer.parseInt(stored);
        } catch (NumberFormatException e) {
            logger.warn(
                    "Preference '{}' has malformed value '{}', deleting and using default: {}",
                    key,
                    stored,
                    defaultValue);
            prefs.remove(key);
            return defaultValue;
        }
    }

    /** Sets int preference. */
    public void putInt(@NonNull String key, int value) {
        prefs.putInt(key, value);
    }

    /** Gets boolean preference, logs if using default. */
    public boolean getBoolean(@NonNull String key, boolean defaultValue) {
        String stored = prefs.get(key, null);
        if (stored == null) {
            logger.warn("Preference '{}' not found, using default value: {}", key, defaultValue);
            return defaultValue;
        }

        if ("true".equalsIgnoreCase(stored)) {
            return true;
        } else if ("false".equalsIgnoreCase(stored)) {
            return false;
        } else {
            logger.warn(
                    "Preference '{}' has malformed value '{}', deleting and using default: {}",
                    key,
                    stored,
                    defaultValue);
            prefs.remove(key);
            return defaultValue;
        }
    }

    /** Sets boolean preference. */
    public void putBoolean(@NonNull String key, boolean value) {
        prefs.putBoolean(key, value);
    }

    /** Gets float preference, logs if using default. */
    public float getFloat(@NonNull String key, float defaultValue) {
        String stored = prefs.get(key, null);
        if (stored == null) {
            logger.warn("Preference '{}' not found, using default value: {}", key, defaultValue);
            return defaultValue;
        }

        try {
            return Float.parseFloat(stored);
        } catch (NumberFormatException e) {
            logger.warn(
                    "Preference '{}' has malformed value '{}', deleting and using default: {}",
                    key,
                    stored,
                    defaultValue);
            prefs.remove(key);
            return defaultValue;
        }
    }

    /** Sets float preference. */
    public void putFloat(@NonNull String key, float value) {
        prefs.putFloat(key, value);
    }

    /** Gets long preference, logs if using default. */
    public long getLong(@NonNull String key, long defaultValue) {
        String stored = prefs.get(key, null);
        if (stored == null) {
            logger.warn("Preference '{}' not found, using default value: {}", key, defaultValue);
            return defaultValue;
        }

        try {
            return Long.parseLong(stored);
        } catch (NumberFormatException e) {
            logger.warn(
                    "Preference '{}' has malformed value '{}', deleting and using default: {}",
                    key,
                    stored,
                    defaultValue);
            prefs.remove(key);
            return defaultValue;
        }
    }

    /** Sets long preference. */
    public void putLong(@NonNull String key, long value) {
        prefs.putLong(key, value);
    }

    /** Gets path preference, validates existence, logs fallback. */
    public String getValidatedPath(@NonNull String key, @NonNull String fallbackPath) {
        String storedPath = prefs.get(key, null);
        if (storedPath == null) {
            logger.warn("Preference '{}' not found, using fallback path: {}", key, fallbackPath);
            return fallbackPath;
        }
        if (new File(storedPath).exists()) {
            return storedPath;
        }
        logger.warn(
                "Stored path '{}' for key '{}' does not exist, using fallback: {}",
                storedPath,
                key,
                fallbackPath);
        return fallbackPath;
    }

    /** Gets path preference with user home fallback. */
    public String getPathWithHomeFallback(@NonNull String key) {
        return getValidatedPath(key, userManager.getUserHomeDir());
    }

    /** Sets directory path preference, extracting parent if given a file path. */
    public void putDirectoryPath(@NonNull String key, @NonNull String path) {
        File file = new File(path);
        String pathToStore = path;
        if (file.isFile()) {
            File parent = file.getParentFile();
            if (parent != null) {
                pathToStore = parent.getPath();
            }
        }
        putString(key, pathToStore);
    }

    /** Returns true if the key exists in preferences without logging. */
    public boolean hasKey(@NonNull String key) {
        return prefs.get(key, null) != null;
    }

    /** Flushes preferences to storage. */
    public void flush() {
        try {
            prefs.flush();
        } catch (Exception e) {
            logger.warn("Failed to flush preferences", e);
        }
    }
}
