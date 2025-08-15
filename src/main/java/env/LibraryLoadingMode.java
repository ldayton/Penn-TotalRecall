package env;

/**
 * Determines how native libraries should be loaded by the application.
 *
 * <p>This enum defines the strategy for loading native libraries like FMOD:
 *
 * <ul>
 *   <li>PACKAGED - Load from the standard system library path (production mode)
 *   <li>UNPACKAGED - Load from development filesystem paths (development mode)
 * </ul>
 */
public enum LibraryLoadingMode {
    /** Load libraries from standard system library path (default for production). */
    PACKAGED,

    /** Load libraries from development filesystem paths (for development/testing). */
    UNPACKAGED
}
