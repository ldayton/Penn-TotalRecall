package info;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/** Default implementation of PreferencesProvider that delegates to Java's Preferences API. */
public class DefaultPreferencesProvider implements PreferencesProvider {
    private final Preferences prefs;

    /** Creates a provider using the default user preferences node for UserPrefs class. */
    public DefaultPreferencesProvider() {
        this(Preferences.userNodeForPackage(UserPrefs.class));
    }

    /**
     * Creates a provider using the specified Preferences node.
     *
     * @param prefs the Preferences node to use
     */
    public DefaultPreferencesProvider(Preferences prefs) {
        this.prefs = prefs;
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        return prefs.getBoolean(key, defaultValue);
    }

    @Override
    public int getInt(String key, int defaultValue) {
        return prefs.getInt(key, defaultValue);
    }

    @Override
    public long getLong(String key, long defaultValue) {
        return prefs.getLong(key, defaultValue);
    }

    @Override
    public float getFloat(String key, float defaultValue) {
        return prefs.getFloat(key, defaultValue);
    }

    @Override
    public String get(String key, String defaultValue) {
        return prefs.get(key, defaultValue);
    }

    @Override
    public void putBoolean(String key, boolean value) {
        prefs.putBoolean(key, value);
    }

    @Override
    public void putInt(String key, int value) {
        prefs.putInt(key, value);
    }

    @Override
    public void putLong(String key, long value) {
        prefs.putLong(key, value);
    }

    @Override
    public void putFloat(String key, float value) {
        prefs.putFloat(key, value);
    }

    @Override
    public void put(String key, String value) {
        prefs.put(key, value);
    }

    @Override
    public void flush() {
        try {
            prefs.flush();
        } catch (BackingStoreException e) {
            throw new RuntimeException("Failed to flush preferences", e);
        }
    }
}
