package info;

/**
 * Interface for accessing user preferences. This abstraction allows for easy testing and
 * alternative implementations.
 */
public interface PreferencesProvider {
    /**
     * Returns the boolean value associated with the specified key.
     *
     * @param key the key whose associated value is to be returned
     * @param defaultValue the value to be returned if this preference node has no value associated
     *     with key
     * @return the boolean value associated with key, or defaultValue if no value is associated with
     *     key
     */
    boolean getBoolean(String key, boolean defaultValue);

    /**
     * Returns the int value associated with the specified key.
     *
     * @param key the key whose associated value is to be returned
     * @param defaultValue the value to be returned if this preference node has no value associated
     *     with key
     * @return the int value associated with key, or defaultValue if no value is associated with key
     */
    int getInt(String key, int defaultValue);

    /**
     * Returns the long value associated with the specified key.
     *
     * @param key the key whose associated value is to be returned
     * @param defaultValue the value to be returned if this preference node has no value associated
     *     with key
     * @return the long value associated with key, or defaultValue if no value is associated with
     *     key
     */
    long getLong(String key, long defaultValue);

    /**
     * Returns the float value associated with the specified key.
     *
     * @param key the key whose associated value is to be returned
     * @param defaultValue the value to be returned if this preference node has no value associated
     *     with key
     * @return the float value associated with key, or defaultValue if no value is associated with
     *     key
     */
    float getFloat(String key, float defaultValue);

    /**
     * Returns the String value associated with the specified key.
     *
     * @param key the key whose associated value is to be returned
     * @param defaultValue the value to be returned if this preference node has no value associated
     *     with key
     * @return the String value associated with key, or defaultValue if no value is associated with
     *     key
     */
    String get(String key, String defaultValue);

    /**
     * Associates the specified boolean value with the specified key.
     *
     * @param key key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     */
    void putBoolean(String key, boolean value);

    /**
     * Associates the specified int value with the specified key.
     *
     * @param key key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     */
    void putInt(String key, int value);

    /**
     * Associates the specified long value with the specified key.
     *
     * @param key key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     */
    void putLong(String key, long value);

    /**
     * Associates the specified float value with the specified key.
     *
     * @param key key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     */
    void putFloat(String key, float value);

    /**
     * Associates the specified String value with the specified key.
     *
     * @param key key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     */
    void put(String key, String value);

    /** Forces any changes to be written to persistent storage synchronously. */
    void flush();
}
