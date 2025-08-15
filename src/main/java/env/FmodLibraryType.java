package env;

/**
 * Determines which variant of the FMOD library should be loaded.
 *
 * <p>This enum defines which version of the FMOD library to use:
 *
 * <ul>
 *   <li>STANDARD - Standard production library (default)
 *   <li>LOGGING - Debug/logging library with additional diagnostic output
 * </ul>
 */
public enum FmodLibraryType {
    /** Standard production library (default for end users). */
    STANDARD,

    /** Debug/logging library with diagnostic output (for development/CI). */
    LOGGING
}
