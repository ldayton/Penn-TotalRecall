package core.audio.fmod;

import app.annotations.ThreadSafe;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import core.audio.exceptions.AudioEngineException;
import java.util.concurrent.locks.ReentrantLock;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages the FMOD system lifecycle including initialization, configuration, and shutdown. This
 * class handles all low-level FMOD system operations and library loading.
 */
@ThreadSafe
@Slf4j
class FmodSystemManager {

    // Core FMOD resources
    private volatile FmodLibrary fmod;
    private volatile Pointer system;
    private volatile boolean initialized = false;

    // Thread safety
    private final ReentrantLock systemLock = new ReentrantLock();

    /** Creates a new FMOD system manager. */
    FmodSystemManager() {}

    /**
     * Initialize the FMOD system. This loads the FMOD library, creates the system, configures it,
     * and initializes it.
     *
     * @throws AudioEngineException if initialization fails
     */
    void initialize() {
        systemLock.lock();
        try {
            if (initialized) {
                throw new AudioEngineException("FMOD system already initialized");
            }

            // Load FMOD library
            fmod = loadFmodLibrary();

            // Create FMOD system
            PointerByReference systemRef = new PointerByReference();
            int result = fmod.FMOD_System_Create(systemRef, FmodConstants.FMOD_VERSION);
            if (result != FmodConstants.FMOD_OK) {
                throw FmodError.toEngineException(result, "create FMOD system");
            }
            system = systemRef.getValue();

            // Configure for playback
            configureForPlayback(fmod, system);

            // Initialize FMOD system
            int maxChannels = 2; // Stereo playback
            int initFlags = FmodConstants.FMOD_INIT_NORMAL;

            result = fmod.FMOD_System_Init(system, maxChannels, initFlags, null);
            if (result != FmodConstants.FMOD_OK) {
                throw FmodError.toEngineException(result, "initialize FMOD system");
            }

            initialized = true;
            logSystemInfo();

        } finally {
            systemLock.unlock();
        }
    }

    /**
     * Load the FMOD native library.
     *
     * @return The loaded FMOD library interface
     * @throws AudioEngineException if the library cannot be loaded
     */
    private FmodLibrary loadFmodLibrary() {
        // Add search path for FMOD libraries
        String resourcePath = getClass().getResource("/fmod/macos").getPath();
        NativeLibrary.addSearchPath("fmod", resourcePath);

        // Load the library
        return Native.load("fmod", FmodLibrary.class);
    }

    /**
     * Configure the FMOD system for low-latency playback.
     *
     * @param fmodLib The FMOD library interface
     * @param sys The FMOD system pointer
     * @throws AudioEngineException if configuration fails
     */
    private void configureForPlayback(@NonNull FmodLibrary fmodLib, @NonNull Pointer sys) {
        // Low latency configuration for playback
        // Smaller buffer for lower latency (256 samples, 4 buffers)
        int result = fmodLib.FMOD_System_SetDSPBufferSize(sys, 256, 4);
        if (result != FmodConstants.FMOD_OK) {
            log.warn(
                    "Could not set DSP buffer size for low latency: {}",
                    FmodError.describe(result));
        }

        // Set software format - mono for audio annotation app
        result =
                fmodLib.FMOD_System_SetSoftwareFormat(
                        sys, 48000, FmodConstants.FMOD_SPEAKERMODE_MONO, 0);
        if (result != FmodConstants.FMOD_OK) {
            log.warn("Could not set software format: {}", FmodError.describe(result));
        }
    }

    /**
     * Log FMOD system information for debugging. Logs version, DSP buffer configuration, and
     * software format.
     */
    private void logSystemInfo() {
        // Logging removed
    }

    /**
     * Update the FMOD system. Should be called periodically to process callbacks.
     *
     * @throws AudioEngineException if update fails
     */
    void update() {
        if (!initialized || fmod == null || system == null) {
            return;
        }

        fmod.FMOD_System_Update(system);
    }

    /**
     * Shutdown the FMOD system and release all resources. This method is idempotent and can be
     * called multiple times safely.
     */
    void shutdown() {
        systemLock.lock();
        try {
            if (!initialized) {
                return; // Already shut down
            }

            if (system != null && fmod != null) {
                int result = fmod.FMOD_System_Release(system);
                if (result != FmodConstants.FMOD_OK) {
                    log.warn("Error releasing FMOD system: {}", FmodError.describe(result));
                }
            }

            system = null;
            fmod = null;
            initialized = false;

        } finally {
            systemLock.unlock();
        }
    }

    /**
     * Check if the FMOD system is initialized and ready for use.
     *
     * @return true if initialized, false otherwise
     */
    boolean isInitialized() {
        return initialized;
    }

    /**
     * Get the FMOD library interface.
     *
     * @return The FMOD library, or null if not initialized
     */
    FmodLibrary getFmodLibrary() {
        return fmod;
    }

    /**
     * Get the FMOD system pointer.
     *
     * @return The system pointer, or null if not initialized
     */
    Pointer getSystem() {
        return system;
    }

    /**
     * Get version information about the loaded FMOD library.
     *
     * @return Version string, or empty if not initialized
     */
    String getVersionInfo() {
        if (!initialized || fmod == null || system == null) {
            return "";
        }

        IntByReference version = new IntByReference();
        IntByReference buildnumber = new IntByReference();
        int result = fmod.FMOD_System_GetVersion(system, version, buildnumber);
        if (result == FmodConstants.FMOD_OK) {
            int v = version.getValue();
            return String.format(
                    "%d.%d.%d (build %d)",
                    (v >> 16) & 0xFFFF, (v >> 8) & 0xFF, v & 0xFF, buildnumber.getValue());
        }
        return "";
    }

    /**
     * Get the current DSP buffer configuration.
     *
     * @return Buffer configuration string, or empty if not initialized
     */
    String getBufferInfo() {
        if (!initialized || fmod == null || system == null) {
            return "";
        }

        IntByReference bufferLength = new IntByReference();
        IntByReference numBuffers = new IntByReference();
        int result = fmod.FMOD_System_GetDSPBufferSize(system, bufferLength, numBuffers);
        if (result == FmodConstants.FMOD_OK) {
            return String.format(
                    "%d samples x %d buffers", bufferLength.getValue(), numBuffers.getValue());
        }
        return "";
    }

    /**
     * Get the current software format configuration.
     *
     * @return Format configuration string, or empty if not initialized
     */
    String getFormatInfo() {
        if (!initialized || fmod == null || system == null) {
            return "";
        }

        IntByReference sampleRate = new IntByReference();
        IntByReference speakerMode = new IntByReference();
        IntByReference numRawSpeakers = new IntByReference();
        int result =
                fmod.FMOD_System_GetSoftwareFormat(system, sampleRate, speakerMode, numRawSpeakers);
        if (result == FmodConstants.FMOD_OK) {
            return String.format(
                    "%d Hz, speaker mode: %d", sampleRate.getValue(), speakerMode.getValue());
        }
        return "";
    }
}
