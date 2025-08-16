package env;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.File;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads FMOD audio libraries with platform-specific configuration.
 *
 * <p>Handles packaged (production) vs unpackaged (development) library loading, custom library
 * paths, and audio hardware detection for headless environments.
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

    // Configuration keys for FMOD settings
    private static final String FMOD_LOADING_MODE_KEY = "fmod.loading.mode";
    private static final String FMOD_LIBRARY_TYPE_KEY = "fmod.library.type";
    private static final String FMOD_LIBRARY_PATH_MACOS_KEY = "fmod.library.path.macos";
    private static final String FMOD_LIBRARY_PATH_LINUX_KEY = "fmod.library.path.linux";
    private static final String FMOD_LIBRARY_PATH_WINDOWS_KEY = "fmod.library.path.windows";
    private static final String AUDIO_HARDWARE_AVAILABLE_KEY = "audio.hardware.available";

    private final AppConfig config;
    private final Platform platform;

    // Thread safety for library loading
    private final Object loadLock = new Object();

    @Inject
    public AudioSystemManager(@NonNull AppConfig config, @NonNull Platform platform) {
        this.config = config;
        this.platform = platform;
    }

    /**
     * Loads the FMOD library using configured loading mode and library type.
     *
     * @param interfaceClass The JNA interface class to load
     * @return The loaded library instance
     * @throws AudioSystemException if library cannot be loaded
     */
    public <T extends Library> T loadAudioLibrary(@NonNull Class<T> interfaceClass) {
        synchronized (loadLock) {
            try {
                LibraryLoadingMode mode = getFmodLoadingMode();
                FmodLibraryType libraryType = getFmodLibraryType();

                logger.debug(
                        "Loading FMOD library: mode={}, type={}, platform={}",
                        mode,
                        libraryType,
                        platform.detect());

                return mode == LibraryLoadingMode.UNPACKAGED
                        ? loadUnpackaged(interfaceClass, libraryType)
                        : loadPackaged(interfaceClass, libraryType);
            } catch (Exception e) {
                throw new AudioSystemException("Failed to load FMOD library", e);
            }
        }
    }

    /**
     * Determines whether audio hardware is available for testing.
     *
     * <p>This controls FMOD output mode configuration:
     *
     * <ul>
     *   <li>true (default) - Use AUTODETECT mode for real audio hardware
     *   <li>false - Use NOSOUND_NRT mode for headless CI testing
     * </ul>
     *
     * @return true if audio hardware is available, false for headless environments
     */
    public boolean isAudioHardwareAvailable() {
        return Boolean.parseBoolean(config.getProperty(AUDIO_HARDWARE_AVAILABLE_KEY, "true"));
    }

    /**
     * Gets the FMOD library loading mode.
     *
     * @return the loading mode, defaults to PACKAGED if not configured
     */
    public LibraryLoadingMode getFmodLoadingMode() {
        String mode = config.getProperty(FMOD_LOADING_MODE_KEY, "packaged");
        try {
            return LibraryLoadingMode.valueOf(mode.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warn(
                    "Invalid FMOD loading mode '{}', defaulting to PACKAGED. Valid values: {}",
                    mode,
                    java.util.Arrays.toString(LibraryLoadingMode.values()));
            return LibraryLoadingMode.PACKAGED;
        }
    }

    /**
     * Gets the FMOD library type (standard or logging).
     *
     * @return the library type, defaults to STANDARD if not configured
     */
    public FmodLibraryType getFmodLibraryType() {
        String type = config.getProperty(FMOD_LIBRARY_TYPE_KEY, "standard");
        try {
            return FmodLibraryType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warn(
                    "Invalid FMOD library type '{}', defaulting to STANDARD. Valid values: {}",
                    type,
                    java.util.Arrays.toString(FmodLibraryType.values()));
            return FmodLibraryType.STANDARD;
        }
    }

    /**
     * Gets the custom FMOD library path for the specified platform.
     *
     * @param platformType the target platform
     * @return the library path, or null if not configured
     */
    public String getFmodLibraryPath(@NonNull Platform.PlatformType platformType) {
        var key =
                switch (platformType) {
                    case MACOS -> FMOD_LIBRARY_PATH_MACOS_KEY;
                    case LINUX -> FMOD_LIBRARY_PATH_LINUX_KEY;
                    case WINDOWS -> FMOD_LIBRARY_PATH_WINDOWS_KEY;
                };
        return config.getProperty(key);
    }

    /**
     * Gets the platform-specific FMOD library filename based on library type.
     *
     * @param libraryType the library type (standard or logging)
     * @return the filename for the FMOD library on the current platform
     */
    public String getFmodLibraryFilename(@NonNull FmodLibraryType libraryType) {
        return switch (platform.detect()) {
            case MACOS ->
                    libraryType == FmodLibraryType.LOGGING ? "libfmodL.dylib" : "libfmod.dylib";
            case LINUX -> libraryType == FmodLibraryType.LOGGING ? "libfmodL.so" : "libfmod.so";
            case WINDOWS -> libraryType == FmodLibraryType.LOGGING ? "fmodL.dll" : "fmod.dll";
        };
    }

    /**
     * Gets the full development path to the FMOD library for the current platform and library type.
     *
     * @param libraryType the library type (standard or logging)
     * @return the relative path to the FMOD library in development mode
     */
    public String getFmodLibraryDevelopmentPath(@NonNull FmodLibraryType libraryType) {
        var platformDir =
                switch (platform.detect()) {
                    case MACOS -> "macos";
                    case LINUX -> "linux";
                    case WINDOWS -> "windows";
                };
        var filename = getFmodLibraryFilename(libraryType);
        return "src/main/resources/fmod/" + platformDir + "/" + filename;
    }

    /**
     * Loads FMOD library from development filesystem paths.
     *
     * @param interfaceClass The JNA interface class to load
     * @param libraryType The library type (standard or logging)
     * @return The loaded library instance
     */
    private <T extends Library> T loadUnpackaged(
            @NonNull Class<T> interfaceClass, @NonNull FmodLibraryType libraryType) {
        var customResult = tryCustomPath(interfaceClass);
        if (customResult != null) {
            return customResult;
        }
        return loadFromDevelopmentPath(interfaceClass, libraryType);
    }

    private <T extends Library> T tryCustomPath(@NonNull Class<T> interfaceClass) {
        var customPath = getFmodLibraryPath(platform.detect());
        if (customPath == null) return null;

        var customFile = new File(customPath);
        if (customFile.exists()) {
            logger.debug("Loading FMOD from custom path: {}", customPath);
            return loadLibraryFromAbsolutePath(customFile.getAbsolutePath(), interfaceClass);
        } else {
            logger.warn("Custom FMOD library path not found: {}", customPath);
            return null;
        }
    }

    private <T extends Library> T loadFromDevelopmentPath(
            @NonNull Class<T> interfaceClass, @NonNull FmodLibraryType libraryType) {
        var projectDir = System.getProperty("user.dir");
        var relativePath = getFmodLibraryDevelopmentPath(libraryType);
        var fullPath = projectDir + "/" + relativePath;

        var libraryFile = new File(fullPath);
        if (!libraryFile.exists()) {
            throw new RuntimeException(
                    "FMOD library not found at: "
                            + fullPath
                            + " (platform="
                            + platform.detect()
                            + ", type="
                            + libraryType
                            + ")");
        }

        logger.debug("Loading FMOD from unpackaged path: {}", fullPath);
        return loadLibraryFromAbsolutePath(libraryFile.getAbsolutePath(), interfaceClass);
    }

    /**
     * Loads FMOD library from standard system library path (packaged mode).
     *
     * @param interfaceClass The JNA interface class to load
     * @param libraryType The library type (standard or logging)
     * @return The loaded library instance
     */
    private <T extends Library> T loadPackaged(
            @NonNull Class<T> interfaceClass, @NonNull FmodLibraryType libraryType) {
        // For packaged mode, we use the system library name without path
        // The exact library depends on the platform and type
        String libraryName = getSystemLibraryName(libraryType);

        logger.debug("Loading FMOD from system library path: {}", libraryName);
        return Native.load(libraryName, interfaceClass);
    }

    /**
     * Loads a native library from an absolute file path using modern JNA API.
     *
     * @param absolutePath The absolute path to the library file
     * @param interfaceClass The JNA interface class to load
     * @return The loaded library instance
     */
    private <T extends Library> T loadLibraryFromAbsolutePath(
            @NonNull String absolutePath, @NonNull Class<T> interfaceClass) {
        var file = new File(absolutePath);
        var fileName = file.getName();

        var libraryName = fileName.replaceAll("^lib", "").replaceAll("\\.(so|dll|dylib)$", "");

        NativeLibrary.addSearchPath(libraryName, file.getParent());
        return Native.load(libraryName, interfaceClass);
    }

    /**
     * Gets the system library name for JNA loading (without file extension).
     *
     * @param libraryType The library type (standard or logging)
     * @return The library name for Native.load()
     */
    private String getSystemLibraryName(@NonNull FmodLibraryType libraryType) {
        // All platforms use the same naming convention
        return libraryType == FmodLibraryType.LOGGING ? "fmodL" : "fmod";
    }
}
