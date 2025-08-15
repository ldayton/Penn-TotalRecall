package audio;

import com.sun.jna.Native;
import env.AppConfig;
import env.Environment;
import env.FmodLibraryType;
import env.LibraryLoadingMode;
import jakarta.inject.Inject;
import java.io.File;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Handles loading of the FMOD native library using application configuration. */
public class FmodLibraryLoader {
    private static final Logger logger = LoggerFactory.getLogger(FmodLibraryLoader.class);
    private final AppConfig config;
    private final Environment env;

    /** Constructor for dependency injection. */
    @Inject
    public FmodLibraryLoader(@NonNull AppConfig config, @NonNull Environment env) {
        this.config = config;
        this.env = env;
    }

    /**
     * Loads the FMOD library using configured loading mode and library type.
     *
     * @param interfaceClass The JNA interface class to load
     * @return The loaded library instance
     * @throws RuntimeException if library cannot be loaded
     */
    public <T> T loadLibrary(Class<T> interfaceClass) {
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
            throw new RuntimeException("Failed to load FMOD library", e);
        }
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
