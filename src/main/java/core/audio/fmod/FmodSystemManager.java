package core.audio.fmod;

import com.google.errorprone.annotations.ThreadSafe;
import com.google.inject.Inject;
import core.audio.exceptions.AudioEngineException;
import core.audio.fmod.panama.FmodCore;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
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
    private volatile MemorySegment system;
    private volatile boolean initialized = false;

    // Thread safety
    private final ReentrantLock systemLock = new ReentrantLock();

    // Library loader
    private final FmodLibraryLoader libraryLoader;

    /** Creates a new FMOD system manager with injected library loader. */
    @Inject
    FmodSystemManager(FmodLibraryLoader libraryLoader) {
        this.libraryLoader = libraryLoader;
    }

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
            // Load native FMOD library for Panama lookup
            libraryLoader.loadNativeLibrary();

            // Create FMOD system
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment systemRef = arena.allocate(ValueLayout.ADDRESS);
                int result = FmodCore.FMOD_System_Create(systemRef, FmodConstants.FMOD_VERSION);
                if (result != FmodConstants.FMOD_OK) {
                    throw FmodError.toEngineException(result, "create FMOD system");
                }
                system = systemRef.get(ValueLayout.ADDRESS, 0);
            }

            // Configure for playback
            configureForPlayback(system);

            // Initialize FMOD system
            int maxChannels = 2; // Stereo playback
            int initFlags = FmodConstants.FMOD_INIT_NORMAL;
            int result =
                    FmodCore.FMOD_System_Init(system, maxChannels, initFlags, MemorySegment.NULL);
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
     * Configure the FMOD system for low-latency playback.
     *
     * @param fmodLib The FMOD library interface
     * @param sys The FMOD system pointer
     * @throws AudioEngineException if configuration fails
     */
    private void configureForPlayback(@NonNull MemorySegment sys) {
        // Low latency configuration for playback
        // Smaller buffer for lower latency (256 samples, 4 buffers)
        int result = FmodCore.FMOD_System_SetDSPBufferSize(sys, 256, 4);
        if (result != FmodConstants.FMOD_OK) {
            log.warn(
                    "Could not set DSP buffer size for low latency: {}",
                    FmodError.describe(result));
        }

        // Set software format - mono for audio annotation app
        result =
                FmodCore.FMOD_System_SetSoftwareFormat(
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
        if (!initialized || system == null) {
            return;
        }

        FmodCore.FMOD_System_Update(system);
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

            if (system != null) {
                int result = FmodCore.FMOD_System_Release(system);
                if (result != FmodConstants.FMOD_OK) {
                    log.warn("Error releasing FMOD system: {}", FmodError.describe(result));
                }
            }

            system = null;
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
    /**
     * Get the FMOD system pointer.
     *
     * @return The system pointer, or null if not initialized
     */
    MemorySegment getSystem() {
        return system;
    }

    /**
     * Get version information about the loaded FMOD library.
     *
     * @return Version string, or empty if not initialized
     */
    String getVersionInfo() {
        if (!initialized || system == null) {
            return "";
        }

        try (Arena arena = Arena.ofConfined()) {
            var version = arena.allocate(ValueLayout.JAVA_INT);
            var buildnumber = arena.allocate(ValueLayout.JAVA_INT);
            int result = FmodCore.FMOD_System_GetVersion(system, version, buildnumber);
            if (result == FmodConstants.FMOD_OK) {
                int v = version.get(ValueLayout.JAVA_INT, 0);
                return String.format(
                        "%d.%d.%d (build %d)",
                        (v >> 16) & 0xFFFF,
                        (v >> 8) & 0xFF,
                        v & 0xFF,
                        buildnumber.get(ValueLayout.JAVA_INT, 0));
            }
        }
        return "";
    }

    /**
     * Get the current DSP buffer configuration.
     *
     * @return Buffer configuration string, or empty if not initialized
     */
    String getBufferInfo() {
        if (!initialized || system == null) {
            return "";
        }

        try (Arena arena = Arena.ofConfined()) {
            var bufferLength = arena.allocate(ValueLayout.JAVA_INT);
            var numBuffers = arena.allocate(ValueLayout.JAVA_INT);
            int result = FmodCore.FMOD_System_GetDSPBufferSize(system, bufferLength, numBuffers);
            if (result == FmodConstants.FMOD_OK) {
                return String.format(
                        "%d samples x %d buffers",
                        bufferLength.get(ValueLayout.JAVA_INT, 0),
                        numBuffers.get(ValueLayout.JAVA_INT, 0));
            }
        }
        return "";
    }

    /**
     * Get the current software format configuration.
     *
     * @return Format configuration string, or empty if not initialized
     */
    String getFormatInfo() {
        if (!initialized || system == null) {
            return "";
        }

        try (Arena arena = Arena.ofConfined()) {
            var sampleRate = arena.allocate(ValueLayout.JAVA_INT);
            var speakerMode = arena.allocate(ValueLayout.JAVA_INT);
            var numRawSpeakers = arena.allocate(ValueLayout.JAVA_INT);
            int result =
                    FmodCore.FMOD_System_GetSoftwareFormat(
                            system, sampleRate, speakerMode, numRawSpeakers);
            if (result == FmodConstants.FMOD_OK) {
                return String.format(
                        "%d Hz, speaker mode: %d",
                        sampleRate.get(ValueLayout.JAVA_INT, 0),
                        speakerMode.get(ValueLayout.JAVA_INT, 0));
            }
        }
        return "";
    }
}
