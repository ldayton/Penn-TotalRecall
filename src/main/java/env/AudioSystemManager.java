package env;

import com.sun.jna.Native;
import env.AudioSystemLoader.AudioSystemException;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.File;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages audio system configuration and FMOD library loading.
 *
 * <p>Handles:
 *
 * <ul>
 *   <li>FMOD library loading (packaged vs unpackaged modes)
 *   <li>Audio system configuration management
 *   <li>Platform-specific audio library handling
 *   <li>Audio hardware availability detection
 * </ul>
 *
 * <p>This class encapsulates all FMOD-related configuration and loading logic, following the same
 * dependency injection pattern as KeyboardManager and LookAndFeelManager.
 */
@Singleton
public class AudioSystemManager implements AudioSystemLoader {

    /**
     * Determines how native libraries should be loaded by the application.
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

    /**
     * Determines which variant of the FMOD library should be loaded.
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
    private static final Logger logger = LoggerFactory.getLogger(AudioSystemManager.class);

    private final AppConfig config;
    private final Environment env;
    
    // Thread safety for library loading
    private final Object loadLock = new Object();

    @Inject
    public AudioSystemManager(@NonNull AppConfig config, @NonNull Environment env) {
        this.config = config;
        this.env = env;
    }

    /**
     * Loads the FMOD library using configured loading mode and library type.
     *
     * @param interfaceClass The JNA interface class to load
     * @return The loaded library instance
     * @throws AudioSystemException if library cannot be loaded
     */
    public <T> T loadAudioLibrary(Class<T> interfaceClass) {
        synchronized (loadLock) {
            try {
                LibraryLoadingMode mode = config.getFmodLoadingMode();
                FmodLibraryType libraryType = config.getFmodLibraryType();

                logger.debug(
                        "Loading FMOD library: mode={}, type={}, platform={}",
                        mode,
                        libraryType,
                        env.getPlatform());

                switch (mode) {
                    case UNPACKAGED:
                        return loadUnpackaged(interfaceClass, libraryType);
                    case PACKAGED:
                    default:
                        return loadPackaged(interfaceClass, libraryType);
                }
            } catch (Exception e) {
                throw new AudioSystemException("Failed to load FMOD library", e);
            }
        }
    }

    /**
     * Determines whether audio hardware is available for testing.
     *
     * @return true if audio hardware is available, false for headless environments
     */
    public boolean isAudioHardwareAvailable() {
        return config.isAudioHardwareAvailable();
    }

    /**
     * Gets the FMOD loading mode from configuration.
     *
     * @return the configured loading mode
     */
    public LibraryLoadingMode getFmodLoadingMode() {
        return config.getFmodLoadingMode();
    }

    /**
     * Gets the FMOD library type from configuration.
     *
     * @return the configured library type
     */
    public FmodLibraryType getFmodLibraryType() {
        return config.getFmodLibraryType();
    }

    /**
     * Loads FMOD library from development filesystem paths.
     *
     * @param interfaceClass The JNA interface class to load
     * @param libraryType The library type (standard or logging)
     * @return The loaded library instance
     */
    private <T> T loadUnpackaged(Class<T> interfaceClass, FmodLibraryType libraryType) {
        // Try custom path from configuration first (supports all platforms)
        String customPath = config.getFmodLibraryPath(env.getPlatform());
        if (customPath != null) {
            File customFile = new File(customPath);
            if (customFile.exists()) {
                logger.debug("Loading FMOD from custom path: {}", customPath);
                return Native.loadLibrary(customFile.getAbsolutePath(), interfaceClass);
            } else {
                logger.warn("Custom FMOD library path not found: {}", customPath);
            }
        }

        // Fall back to default development path for current platform
        String projectDir = System.getProperty("user.dir");
        String relativePath = env.getFmodLibraryDevelopmentPath(libraryType);
        String fullPath = projectDir + "/" + relativePath;

        File libraryFile = new File(fullPath);
        if (!libraryFile.exists()) {
            throw new RuntimeException(
                    "FMOD library not found at: "
                            + fullPath
                            + " (platform="
                            + env.getPlatform()
                            + ", type="
                            + libraryType
                            + ")");
        }

        logger.debug("Loading FMOD from unpackaged path: {}", fullPath);
        return Native.loadLibrary(libraryFile.getAbsolutePath(), interfaceClass);
    }

    /**
     * Loads FMOD library from standard system library path (packaged mode).
     *
     * @param interfaceClass The JNA interface class to load
     * @param libraryType The library type (standard or logging)
     * @return The loaded library instance
     */
    private <T> T loadPackaged(Class<T> interfaceClass, FmodLibraryType libraryType) {
        // For packaged mode, we use the system library name without path
        // The exact library depends on the platform and type
        String libraryName = getSystemLibraryName(libraryType);

        logger.debug("Loading FMOD from system library path: {}", libraryName);
        return Native.loadLibrary(libraryName, interfaceClass);
    }

    /**
     * Gets the system library name for JNA loading (without file extension).
     *
     * @param libraryType The library type (standard or logging)
     * @return The library name for Native.loadLibrary()
     */
    private String getSystemLibraryName(FmodLibraryType libraryType) {
        // All platforms use the same naming convention
        return libraryType == FmodLibraryType.LOGGING ? "fmodL" : "fmod";
    }
}