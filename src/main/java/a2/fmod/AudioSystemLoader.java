package a2.fmod;

import com.sun.jna.Library;
import lombok.NonNull;

/**
 * Generic interface for loading native audio libraries with dependency injection support.
 *
 * <h3>Design Purpose</h3>
 *
 * <ul>
 *   <li>Abstracts audio engine choice from client code
 *   <li>Enables testing with mock implementations
 *   <li>Supports platform-specific library loading strategies
 *   <li>Provides hardware detection for headless environments
 * </ul>
 *
 * <h3>Implementation Notes</h3>
 *
 * <ul>
 *   <li>Current implementation uses FMOD Core (implementation detail)
 *   <li>Could be swapped for OpenAL, BASS, etc. without client code changes
 *   <li>Singleton lifecycle managed by dependency injection
 *   <li>Thread-safe library loading with synchronization
 * </ul>
 */
public interface AudioSystemLoader {

    /**
     * Loads a native audio library using JNA.
     *
     * @param <T> the JNA interface type
     * @param interfaceClass the JNA interface class to load
     * @return the loaded library instance
     * @throws AudioSystemException if the library cannot be loaded
     * @implNote Thread-safe: synchronized loading with platform detection
     * @implNote Supports both packaged (production) and unpackaged (development) modes
     */
    <T extends Library> T loadAudioLibrary(@NonNull Class<T> interfaceClass);

    /**
     * Checks if audio hardware is available for output.
     *
     * @return true if audio hardware is present, false for headless CI environments
     * @implNote Used to determine audio output mode (hardware vs silent)
     * @implNote Configurable via audio.hardware.available property
     */
    boolean isAudioHardwareAvailable();

    /** Thrown when audio library loading fails. */
    class AudioSystemException extends RuntimeException {
        public AudioSystemException(@NonNull String message, @NonNull Throwable cause) {
            super(message, cause);
        }
    }
}
