package a2.fmod;

import a2.exceptions.AudioEngineException;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import env.AppConfig;
import env.Platform;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.File;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Platform-aware native audio library loader with flexible deployment modes.
 *
 * <h3>Loading Modes</h3>
 *
 * <ul>
 *   <li>PACKAGED: Load from system library path (production deployment)
 *   <li>UNPACKAGED: Load from development filesystem paths (development/testing)
 *   <li>Custom paths supported via configuration for both modes
 *   <li>Automatic platform detection (macOS, Linux, Windows)
 * </ul>
 *
 * <h3>Library Types</h3>
 *
 * <ul>
 *   <li>STANDARD: Production library (optimized, minimal logging)
 *   <li>LOGGING: Debug library (verbose diagnostics, performance logging)
 *   <li>Platform-specific filename resolution (dylib/so/dll)
 *   <li>Automatic selection based on configuration
 * </ul>
 *
 * <h3>Configuration System</h3>
 *
 * <ul>
 *   <li>audio.loading.mode: packaged|unpackaged (default: packaged)
 *   <li>audio.library.type: standard|logging (default: standard)
 *   <li>audio.library.path.{platform}: Custom library paths
 *   <li>audio.hardware.available: Hardware detection override
 * </ul>
 *
 * <h3>Threading & Safety</h3>
 *
 * <ul>
 *   <li>Thread-safe library loading with synchronized access
 *   <li>Singleton lifecycle managed by dependency injection
 *   <li>Modern JNA loading (Native.load + addSearchPath)
 *   <li>Comprehensive error handling and fallback strategies
 * </ul>
 *
 * <h3>Development vs Production</h3>
 *
 * <ul>
 *   <li>Development: Libraries loaded from src/main/resources/
 *   <li>Production: Libraries loaded from system paths or app bundle
 *   <li>CI/Testing: NOSOUND mode for headless environments
 *   <li>Custom paths: Override default locations via configuration
 * </ul>
 */
@Singleton
public class FmodLibraryLoader {

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
     * Determines which variant of the audio library should be loaded.
     *
     * <ul>
     *   <li>STANDARD - Standard production library (default)
     *   <li>LOGGING - Debug/logging library with additional diagnostic output
     * </ul>
     */
    public enum LibraryType {
        /** Standard production library (default for end users). */
        STANDARD,

        /** Debug/logging library with diagnostic output (for development/CI). */
        LOGGING
    }

    private static final Logger logger = LoggerFactory.getLogger(FmodLibraryLoader.class);

    // Configuration keys for audio system settings
    private static final String LOADING_MODE_KEY = "audio.loading.mode";
    private static final String LIBRARY_TYPE_KEY = "audio.library.type";
    private static final String LIBRARY_PATH_MACOS_KEY = "audio.library.path.macos";
    private static final String LIBRARY_PATH_LINUX_KEY = "audio.library.path.linux";
    private static final String LIBRARY_PATH_WINDOWS_KEY = "audio.library.path.windows";
    private static final String AUDIO_HARDWARE_AVAILABLE_KEY = "audio.hardware.available";

    private final AppConfig config;
    private final Platform platform;

    // Thread safety for library loading
    private final Object loadLock = new Object();

    @Inject
    public FmodLibraryLoader(@NonNull AppConfig config, @NonNull Platform platform) {
        this.config = config;
        this.platform = platform;
    }

    /**
     * Loads the audio library using configured loading mode and library type.
     *
     * @param interfaceClass The JNA interface class to load
     * @return The loaded library instance
     * @throws AudioEngineException if library cannot be loaded
     */
    public <T extends Library> T loadAudioLibrary(@NonNull Class<T> interfaceClass) {
        synchronized (loadLock) {
            try {
                LibraryLoadingMode mode = getLoadingMode();
                LibraryType libraryType = getLibraryType();

                logger.debug(
                        "Loading audio library: mode={}, type={}, platform={}",
                        mode,
                        libraryType,
                        platform.detect());

                return mode == LibraryLoadingMode.UNPACKAGED
                        ? loadUnpackaged(interfaceClass, libraryType)
                        : loadPackaged(interfaceClass, libraryType);
            } catch (Exception e) {
                throw new AudioEngineException("Failed to load audio library", e);
            }
        }
    }

    /**
     * Determines whether audio hardware is available for testing.
     *
     * <p>This controls audio output mode configuration:
     *
     * <ul>
     *   <li>true (default) - Use hardware audio output
     *   <li>false - Use silent mode for headless CI testing
     * </ul>
     *
     * @return true if audio hardware is available, false for headless environments
     */
    public boolean isAudioHardwareAvailable() {
        return Boolean.parseBoolean(config.getProperty(AUDIO_HARDWARE_AVAILABLE_KEY, "true"));
    }

    /**
     * Gets the audio library loading mode.
     *
     * @return the loading mode, defaults to PACKAGED if not configured
     */
    public LibraryLoadingMode getLoadingMode() {
        String mode = config.getProperty(LOADING_MODE_KEY, "packaged");
        try {
            return LibraryLoadingMode.valueOf(mode.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warn(
                    "Invalid audio loading mode '{}', defaulting to PACKAGED. Valid values: {}",
                    mode,
                    java.util.Arrays.toString(LibraryLoadingMode.values()));
            return LibraryLoadingMode.PACKAGED;
        }
    }

    /**
     * Gets the audio library type (standard or logging).
     *
     * @return the library type, defaults to STANDARD if not configured
     */
    public LibraryType getLibraryType() {
        String type = config.getProperty(LIBRARY_TYPE_KEY, "standard");
        try {
            return LibraryType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warn(
                    "Invalid audio library type '{}', defaulting to STANDARD. Valid values: {}",
                    type,
                    java.util.Arrays.toString(LibraryType.values()));
            return LibraryType.STANDARD;
        }
    }

    /**
     * Gets the custom audio library path for the specified platform.
     *
     * @param platformType the target platform
     * @return the library path, or null if not configured
     */
    public String getLibraryPath(@NonNull Platform.PlatformType platformType) {
        var key =
                switch (platformType) {
                    case MACOS -> LIBRARY_PATH_MACOS_KEY;
                    case LINUX -> LIBRARY_PATH_LINUX_KEY;
                    case WINDOWS -> LIBRARY_PATH_WINDOWS_KEY;
                };
        return config.getProperty(key);
    }

    /**
     * Gets the platform-specific audio library filename based on library type.
     *
     * @param libraryType the library type (standard or logging)
     * @return the filename for the audio library on the current platform
     */
    public String getLibraryFilename(@NonNull LibraryType libraryType) {
        return switch (platform.detect()) {
            case MACOS -> libraryType == LibraryType.LOGGING ? "libfmodL.dylib" : "libfmod.dylib";
            case LINUX -> libraryType == LibraryType.LOGGING ? "libfmodL.so" : "libfmod.so";
            case WINDOWS -> libraryType == LibraryType.LOGGING ? "fmodL.dll" : "fmod.dll";
        };
    }

    /**
     * Gets the full development path to the audio library for the current platform and library
     * type.
     *
     * @param libraryType the library type (standard or logging)
     * @return the relative path to the audio library in development mode
     */
    public String getLibraryDevelopmentPath(@NonNull LibraryType libraryType) {
        var platformDir =
                switch (platform.detect()) {
                    case MACOS -> "macos";
                    case LINUX -> "linux";
                    case WINDOWS -> "windows";
                };
        var filename = getLibraryFilename(libraryType);
        // Note: Currently hardcoded to FMOD resources - implementation detail
        return "src/main/resources/fmod/" + platformDir + "/" + filename;
    }

    /**
     * Loads audio library from development filesystem paths.
     *
     * @param interfaceClass The JNA interface class to load
     * @param libraryType The library type (standard or logging)
     * @return The loaded library instance
     */
    private <T extends Library> T loadUnpackaged(
            @NonNull Class<T> interfaceClass, @NonNull LibraryType libraryType) {
        var customResult = tryCustomPath(interfaceClass);
        if (customResult != null) {
            return customResult;
        }
        return loadFromDevelopmentPath(interfaceClass, libraryType);
    }

    private <T extends Library> T tryCustomPath(@NonNull Class<T> interfaceClass) {
        var customPath = getLibraryPath(platform.detect());
        if (customPath == null) return null;

        var customFile = new File(customPath);
        if (customFile.exists()) {
            logger.debug("Loading audio library from custom path: {}", customPath);
            return loadLibraryFromAbsolutePath(customFile.getAbsolutePath(), interfaceClass);
        } else {
            logger.warn("Custom audio library path not found: {}", customPath);
            return null;
        }
    }

    private <T extends Library> T loadFromDevelopmentPath(
            @NonNull Class<T> interfaceClass, @NonNull LibraryType libraryType) {
        var projectDir = System.getProperty("user.dir");
        var relativePath = getLibraryDevelopmentPath(libraryType);
        var fullPath = projectDir + "/" + relativePath;

        var libraryFile = new File(fullPath);
        if (!libraryFile.exists()) {
            throw new RuntimeException(
                    "Audio library not found at: "
                            + fullPath
                            + " (platform="
                            + platform.detect()
                            + ", type="
                            + libraryType
                            + ")");
        }

        logger.debug("Loading audio library from unpackaged path: {}", fullPath);
        return loadLibraryFromAbsolutePath(libraryFile.getAbsolutePath(), interfaceClass);
    }

    /**
     * Loads audio library from standard system library path (packaged mode).
     *
     * @param interfaceClass The JNA interface class to load
     * @param libraryType The library type (standard or logging)
     * @return The loaded library instance
     */
    private <T extends Library> T loadPackaged(
            @NonNull Class<T> interfaceClass, @NonNull LibraryType libraryType) {
        // For packaged mode, we use the system library name without path
        // The exact library depends on the platform and type
        String libraryName = getSystemLibraryName(libraryType);

        logger.debug("Loading audio library from system library path: {}", libraryName);
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
    private String getSystemLibraryName(@NonNull LibraryType libraryType) {
        // All platforms use the same naming convention for FMOD
        return libraryType == LibraryType.LOGGING ? "fmodL" : "fmod";
    }
}
