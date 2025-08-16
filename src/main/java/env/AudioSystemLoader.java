package env;

import env.AudioSystemManager.FmodLibraryType;
import env.AudioSystemManager.LibraryLoadingMode;

/**
 * Interface for audio system loading and configuration management.
 *
 * <p>Defines the contract for loading native audio libraries and managing audio system
 * configuration. This interface enables dependency injection and testing by providing an
 * abstraction over the concrete audio system implementation.
 *
 * <p>Implementations must be thread-safe for use in dependency injection containers.
 */
public interface AudioSystemLoader {

    /**
     * Loads the specified audio library using the configured loading mode and library type.
     *
     * <p>This method handles platform-specific loading strategies and configuration-based library
     * selection. The implementation must be thread-safe.
     *
     * @param <T> the library interface type
     * @param interfaceClass the JNA interface class to load
     * @return the loaded library instance
     * @throws AudioSystemException if the library cannot be loaded
     */
    <T> T loadAudioLibrary(Class<T> interfaceClass);

    /**
     * Determines whether audio hardware is available for testing.
     *
     * @return true if audio hardware is available, false for headless environments
     */
    boolean isAudioHardwareAvailable();

    /**
     * Gets the FMOD loading mode from configuration.
     *
     * @return the configured loading mode
     */
    LibraryLoadingMode getFmodLoadingMode();

    /**
     * Gets the FMOD library type from configuration.
     *
     * @return the configured library type
     */
    FmodLibraryType getFmodLibraryType();

    /**
     * Exception thrown when audio system operations fail.
     *
     * <p>This exception indicates failures in audio system initialization, library loading, or
     * configuration. It provides context about the specific audio system operation that failed.
     */
    class AudioSystemException extends RuntimeException {

        /**
         * Constructs an AudioSystemException with the specified detail message.
         *
         * @param message the detail message
         */
        public AudioSystemException(String message) {
            super(message);
        }

        /**
         * Constructs an AudioSystemException with the specified detail message and cause.
         *
         * @param message the detail message
         * @param cause the cause of this exception
         */
        public AudioSystemException(String message, Throwable cause) {
            super(message, cause);
        }

        /**
         * Constructs an AudioSystemException with the specified cause.
         *
         * @param cause the cause of this exception
         */
        public AudioSystemException(Throwable cause) {
            super(cause);
        }
    }
}