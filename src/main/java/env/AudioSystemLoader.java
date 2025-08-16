package env;

import lombok.NonNull;

/**
 * Interface for loading native audio libraries.
 *
 * <p>Exists to enable dependency injection and testing. The concrete implementation handles
 * platform-specific library loading details and configuration.
 */
public interface AudioSystemLoader {

    /**
     * Loads a native audio library using JNA.
     *
     * @param <T> the JNA interface type
     * @param interfaceClass the JNA interface class to load
     * @return the loaded library instance
     * @throws AudioSystemException if the library cannot be loaded
     */
    <T> T loadAudioLibrary(@NonNull Class<T> interfaceClass);

    /**
     * Checks if audio hardware is available.
     *
     * @return true if audio hardware is present, false for headless environments
     */
    boolean isAudioHardwareAvailable();

    /** Thrown when audio library loading fails. */
    class AudioSystemException extends RuntimeException {
        public AudioSystemException(@NonNull String message, @NonNull Throwable cause) {
            super(message, cause);
        }
    }
}
