package audio;

import com.sun.jna.Native;
import java.io.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.AppConfig;
import util.LibraryLoadingMode;

/** Handles loading of the FMOD native library using application configuration. */
public class FmodLibraryLoader {
    private static final Logger logger = LoggerFactory.getLogger(FmodLibraryLoader.class);
    private final AppConfig config;

    /** Default constructor using application configuration. */
    public FmodLibraryLoader() {
        this(AppConfig.getInstance());
    }

    /** Constructor for dependency injection. */
    public FmodLibraryLoader(AppConfig config) {
        this.config = config;
    }

    /**
     * Loads the FMOD library using configured loading mode.
     *
     * @param interfaceClass The JNA interface class to load
     * @return The loaded library instance
     * @throws RuntimeException if library cannot be loaded
     */
    public <T> T loadLibrary(Class<T> interfaceClass) {
        try {
            LibraryLoadingMode mode = config.getFmodLoadingMode();
            logger.debug("Loading FMOD library in {} mode", mode);

            switch (mode) {
                case UNPACKAGED:
                    return loadUnpackaged(interfaceClass);
                case PACKAGED:
                default:
                    return loadPackaged(interfaceClass);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load FMOD library", e);
        }
    }

    /**
     * Loads FMOD library from development filesystem paths.
     *
     * @param interfaceClass The JNA interface class to load
     * @return The loaded library instance
     */
    private <T> T loadUnpackaged(Class<T> interfaceClass) {
        // Try custom path from configuration first
        String customPath = config.getFmodLibraryPathMacOS();
        if (customPath != null) {
            File customFile = new File(customPath);
            if (customFile.exists()) {
                logger.debug("Loading FMOD from custom path: {}", customPath);
                return Native.loadLibrary(customFile.getAbsolutePath(), interfaceClass);
            } else {
                logger.warn("Custom FMOD library path not found: {}", customPath);
            }
        }

        // Fall back to default development path
        String projectDir = System.getProperty("user.dir");
        String defaultPath = projectDir + "/src/main/resources/fmod/macos/libfmod.dylib";
        File defaultFile = new File(defaultPath);
        if (!defaultFile.exists()) {
            throw new RuntimeException("FMOD library not found at: " + defaultPath);
        }

        logger.debug("Loading FMOD from default unpackaged path: {}", defaultPath);
        return Native.loadLibrary(defaultFile.getAbsolutePath(), interfaceClass);
    }

    /**
     * Loads FMOD library from standard system library path (packaged mode).
     *
     * @param interfaceClass The JNA interface class to load
     * @return The loaded library instance
     */
    private <T> T loadPackaged(Class<T> interfaceClass) {
        logger.debug("Loading FMOD from system library path");
        return Native.loadLibrary("fmod", interfaceClass);
    }
}
