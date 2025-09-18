package core.audio.fmod;

import core.env.AppConfig;
import lombok.NonNull;

/** Lightweight configuration holder for FMOD settings using the project's AppConfig. */
public class FmodProperties {
    private static final String KEY_LOADING_MODE = "audio.loading.mode";
    private static final String KEY_LIBRARY_TYPE = "audio.library.type";
    private static final String KEY_LIBRARY_PATH_MACOS = "audio.library.path.macos";
    private static final String KEY_LIBRARY_PATH_WINDOWS = "audio.library.path.windows";
    private static final String KEY_LIBRARY_PATH_LINUX = "audio.library.path.linux";

    private static final String DEFAULT_LOADING_MODE = "packaged";
    private static final String DEFAULT_LIBRARY_TYPE = "standard";
    private static final String DEFAULT_LIBRARY_PATH_MACOS = "src/main/resources/fmod/macos";
    private static final String DEFAULT_LIBRARY_PATH_WINDOWS = "src/main/resources/fmod/windows";
    private static final String DEFAULT_LIBRARY_PATH_LINUX = "src/main/resources/fmod/linux";

    private final String loadingMode;
    private final String libraryType;
    private final String libraryPathMacos;
    private final String libraryPathWindows;
    private final String libraryPathLinux;

    public FmodProperties() {
        this(new AppConfig());
    }

    public FmodProperties(@NonNull AppConfig config) {
        this.loadingMode = config.getProperty(KEY_LOADING_MODE, DEFAULT_LOADING_MODE);
        this.libraryType = config.getProperty(KEY_LIBRARY_TYPE, DEFAULT_LIBRARY_TYPE);
        this.libraryPathMacos =
                config.getProperty(KEY_LIBRARY_PATH_MACOS, DEFAULT_LIBRARY_PATH_MACOS);
        this.libraryPathWindows =
                config.getProperty(KEY_LIBRARY_PATH_WINDOWS, DEFAULT_LIBRARY_PATH_WINDOWS);
        this.libraryPathLinux =
                config.getProperty(KEY_LIBRARY_PATH_LINUX, DEFAULT_LIBRARY_PATH_LINUX);
    }

    public FmodProperties(@NonNull String loadingMode, @NonNull String libraryType) {
        this.loadingMode = loadingMode;
        this.libraryType = libraryType;
        this.libraryPathMacos = DEFAULT_LIBRARY_PATH_MACOS;
        this.libraryPathWindows = DEFAULT_LIBRARY_PATH_WINDOWS;
        this.libraryPathLinux = DEFAULT_LIBRARY_PATH_LINUX;
    }

    public FmodProperties(
            @NonNull String loadingMode,
            @NonNull String libraryType,
            @NonNull String libraryPathMacos,
            @NonNull String libraryPathWindows,
            @NonNull String libraryPathLinux) {
        this.loadingMode = loadingMode;
        this.libraryType = libraryType;
        this.libraryPathMacos = libraryPathMacos;
        this.libraryPathWindows = libraryPathWindows;
        this.libraryPathLinux = libraryPathLinux;
    }

    public String loadingMode() {
        return loadingMode;
    }

    public String libraryType() {
        return libraryType;
    }

    public String libraryPathMacos() {
        return libraryPathMacos;
    }

    public String libraryPathWindows() {
        return libraryPathWindows;
    }

    public String libraryPathLinux() {
        return libraryPathLinux;
    }

    /** Defaults helper retained for test compatibility. */
    public static class FmodDefaults {
        public static final String MACOS_LIB_PATH = DEFAULT_LIBRARY_PATH_MACOS;
    }
}
