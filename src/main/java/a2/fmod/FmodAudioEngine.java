package a2.fmod;

import a2.AudioBuffer;
import a2.AudioEngine;
import a2.AudioEngineConfig;
import a2.AudioHandle;
import a2.AudioMetadata;
import a2.PlaybackHandle;
import a2.PlaybackState;
import a2.fmod.exceptions.CorruptedAudioFileException;
import a2.fmod.exceptions.UnsupportedAudioFormatException;
import app.annotations.ThreadSafe;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/** FMOD-based implementation of AudioEngine. Uses FMOD Core API via JNA for audio operations. */
@ThreadSafe
@Slf4j
public class FmodAudioEngine implements AudioEngine {

    private volatile FmodLibrary fmod;
    private volatile Pointer system;
    private volatile AudioEngineConfig config;

    // State machine for proper lifecycle management
    private enum State {
        UNINITIALIZED,
        INITIALIZING,
        INITIALIZED,
        CLOSING,
        CLOSED
    }

    private final AtomicReference<State> state = new AtomicReference<>(State.UNINITIALIZED);
    private final ReentrantLock operationLock = new ReentrantLock();

    // Resource management
    private final Map<Long, Pointer> activeChannels = new ConcurrentHashMap<>();
    private final AtomicLong nextHandleId = new AtomicLong(1);
    private final AtomicLong nextPlaybackId = new AtomicLong(1);

    // Current loaded audio (users work with one file at a time)
    private FmodAudioHandle currentHandle;
    private Pointer currentSound;
    private String currentPath;

    /** Default constructor for factory use. */
    FmodAudioEngine() {}

    /** Package-private initialization method called by factory. */
    void init(@NonNull AudioEngineConfig config) {
        // Transition from UNINITIALIZED to INITIALIZING
        if (!state.compareAndSet(State.UNINITIALIZED, State.INITIALIZING)) {
            State currentState = state.get();
            throw new IllegalStateException("Cannot initialize engine in state: " + currentState);
        }

        FmodLibrary fmodLib = null;
        Pointer newSystem = null;

        try {
            log.info("Initializing FMOD audio engine with config: {}", config);
            validateConfig(config);

            // Load FMOD library and create system (no lock needed, these are local operations)
            fmodLib = loadFmodLibrary();

            // Create FMOD system
            PointerByReference systemRef = new PointerByReference();
            int result = fmodLib.FMOD_System_Create(systemRef, FmodConstants.FMOD_VERSION);
            if (result != FmodConstants.FMOD_OK) {
                throw new RuntimeException(
                        "Failed to create FMOD system: "
                                + fmodLib.FMOD_ErrorString(result)
                                + " (code: "
                                + result
                                + ")");
            }
            newSystem = systemRef.getValue();

            // Configure based on mode
            configureForMode(fmodLib, newSystem, config.getMode());

            // Initialize FMOD system
            int maxChannels =
                    config.getMode() == AudioEngineConfig.Mode.PLAYBACK
                            ? 2
                            : 1; // 2 for transitions, 1 for rendering
            int initFlags = FmodConstants.FMOD_INIT_NORMAL;

            result = fmodLib.FMOD_System_Init(newSystem, maxChannels, initFlags, null);
            if (result != FmodConstants.FMOD_OK) {
                throw new RuntimeException(
                        "Failed to initialize FMOD system: "
                                + fmodLib.FMOD_ErrorString(result)
                                + " (code: "
                                + result
                                + ")");
            }

            // Update shared state and transition to INITIALIZED
            this.system = newSystem;
            this.fmod = fmodLib;
            this.config = config;

            // Transition to INITIALIZED
            if (!state.compareAndSet(State.INITIALIZING, State.INITIALIZED)) {
                // Someone called close() while we were initializing
                throw new IllegalStateException("Engine was closed during initialization");
            }

            // Log system info (after lock released)
            logSystemInfo();
            log.info("FMOD audio engine initialized successfully");

        } catch (Exception e) {
            // Clean up any resources we created
            if (newSystem != null && fmodLib != null) {
                try {
                    fmodLib.FMOD_System_Release(newSystem);
                } catch (Exception cleanupEx) {
                    log.debug("Error during cleanup after init failure", cleanupEx);
                }
            }

            // Reset state to allow retry (only if we're still INITIALIZING)
            // If close() was called, state will be CLOSED and we should leave it
            state.compareAndSet(State.INITIALIZING, State.UNINITIALIZED);

            // Propagate the original exception
            throw e;
        }
    }

    private void configureForMode(
            @NonNull FmodLibrary fmodLib,
            @NonNull Pointer sys,
            @NonNull AudioEngineConfig.Mode mode) {
        if (mode == AudioEngineConfig.Mode.PLAYBACK) {
            // Low latency configuration for playback
            // Smaller buffer for lower latency (256 samples, 4 buffers)
            int result = fmodLib.FMOD_System_SetDSPBufferSize(sys, 256, 4);
            if (result != FmodConstants.FMOD_OK) {
                log.warn(
                        "Could not set DSP buffer size for low latency: {}",
                        fmodLib.FMOD_ErrorString(result));
            }

            // Set software format - mono for audio annotation app
            result =
                    fmodLib.FMOD_System_SetSoftwareFormat(
                            sys, 48000, FmodConstants.FMOD_SPEAKERMODE_MONO, 0);
            if (result != FmodConstants.FMOD_OK) {
                log.warn("Could not set software format: {}", fmodLib.FMOD_ErrorString(result));
            }

        } else {
            // RENDERING mode - no audio output needed, just reading samples
            // Use NOSOUND_NRT for faster-than-realtime processing without audio device
            int result =
                    fmodLib.FMOD_System_SetOutput(sys, FmodConstants.FMOD_OUTPUTTYPE_NOSOUND_NRT);
            if (result != FmodConstants.FMOD_OK) {
                log.warn(
                        "Could not set NOSOUND_NRT output for rendering: {}",
                        fmodLib.FMOD_ErrorString(result));
            }

            // Larger buffers for rendering efficiency (2048 samples, 2 buffers)
            result = fmodLib.FMOD_System_SetDSPBufferSize(sys, 2048, 2);
            if (result != FmodConstants.FMOD_OK) {
                log.warn(
                        "Could not set DSP buffer size for rendering: {}",
                        fmodLib.FMOD_ErrorString(result));
            }

            // Mono format for rendering as well
            result =
                    fmodLib.FMOD_System_SetSoftwareFormat(
                            sys, 48000, FmodConstants.FMOD_SPEAKERMODE_MONO, 0);
            if (result != FmodConstants.FMOD_OK) {
                log.warn("Could not set software format: {}", fmodLib.FMOD_ErrorString(result));
            }
        }
    }

    private void logSystemInfo() {
        IntByReference version = new IntByReference();
        int result = fmod.FMOD_System_GetVersion(system, version);
        if (result == FmodConstants.FMOD_OK) {
            int v = version.getValue();
            log.info("FMOD version: {}.{}.{}", (v >> 16) & 0xFFFF, (v >> 8) & 0xFF, v & 0xFF);
        }

        IntByReference bufferLength = new IntByReference();
        IntByReference numBuffers = new IntByReference();
        result = fmod.FMOD_System_GetDSPBufferSize(system, bufferLength, numBuffers);
        if (result == FmodConstants.FMOD_OK) {
            log.info(
                    "DSP buffer configuration: {} samples x {} buffers",
                    bufferLength.getValue(),
                    numBuffers.getValue());
        }

        IntByReference sampleRate = new IntByReference();
        IntByReference speakerMode = new IntByReference();
        IntByReference numRawSpeakers = new IntByReference();
        result =
                fmod.FMOD_System_GetSoftwareFormat(system, sampleRate, speakerMode, numRawSpeakers);
        if (result == FmodConstants.FMOD_OK) {
            log.info(
                    "Software format: {} Hz, speaker mode: {}",
                    sampleRate.getValue(),
                    speakerMode.getValue());
        }
    }

    private void checkResult(int result, String message) {
        if (result != FmodConstants.FMOD_OK) {
            throw new RuntimeException(
                    message + ": " + fmod.FMOD_ErrorString(result) + " (code: " + result + ")");
        }
    }

    private void validateConfig(@NonNull AudioEngineConfig config) {
        // Validate cache size (minimum 1MB, maximum 10GB)
        long cacheBytes = config.getMaxCacheBytes();
        if (cacheBytes < 1024 * 1024) {
            throw new IllegalArgumentException(
                    "maxCacheBytes must be at least 1MB, got: " + cacheBytes);
        }
        if (cacheBytes > 10L * 1024 * 1024 * 1024) {
            throw new IllegalArgumentException(
                    "maxCacheBytes must not exceed 10GB, got: " + cacheBytes);
        }

        // Validate prefetch window (0-60 seconds)
        int prefetchSeconds = config.getPrefetchWindowSeconds();
        if (prefetchSeconds < 0) {
            throw new IllegalArgumentException(
                    "prefetchWindowSeconds must be non-negative, got: " + prefetchSeconds);
        }
        if (prefetchSeconds > 60) {
            throw new IllegalArgumentException(
                    "prefetchWindowSeconds must not exceed 60 seconds, got: " + prefetchSeconds);
        }
    }

    private FmodLibrary loadFmodLibrary() {
        return doLoadFmodLibrary();
    }

    // Package-private for testing - can be overridden by spy
    FmodLibrary doLoadFmodLibrary() {
        // Add search path for FMOD libraries
        String resourcePath = getClass().getResource("/fmod/macos").getPath();
        NativeLibrary.addSearchPath("fmod", resourcePath);

        // Load the library
        return Native.load("fmod", FmodLibrary.class);
    }

    private void checkOperational() {
        State currentState = state.get();
        if (currentState != State.INITIALIZED) {
            throw new IllegalStateException("Operation not allowed in state: " + currentState);
        }
    }

    @Override
    public AudioHandle loadAudio(@NonNull String filePath)
            throws FileNotFoundException,
                    UnsupportedAudioFormatException,
                    CorruptedAudioFileException {
        checkOperational();

        // Validate file exists and get canonical path
        File file = new File(filePath);
        if (!file.exists()) {
            throw new FileNotFoundException("Audio file not found: " + filePath);
        }
        if (!file.canRead()) {
            throw new IllegalArgumentException("Cannot read audio file: " + filePath);
        }

        String canonicalPath;
        try {
            canonicalPath = file.getCanonicalPath();
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot resolve file path: " + filePath, e);
        }

        operationLock.lock();
        try {
            // If same file already loaded, return existing handle
            if (currentPath != null && currentPath.equals(canonicalPath) && currentHandle != null) {
                log.debug("Returning existing handle for: {}", canonicalPath);
                return currentHandle;
            }

            // Release previous sound if any
            if (currentSound != null) {
                log.debug("Releasing previous audio file: {}", currentPath);
                int result = fmod.FMOD_Sound_Release(currentSound);
                if (result != FmodConstants.FMOD_OK) {
                    log.warn("Error releasing previous sound: {}", fmod.FMOD_ErrorString(result));
                }
                currentSound = null;
                currentPath = null;
                currentHandle = null;

                // Ensure FMOD completes cleanup before loading new file
                result = fmod.FMOD_System_Update(system);
                if (result != FmodConstants.FMOD_OK) {
                    log.debug("Error during system update: {}", fmod.FMOD_ErrorString(result));
                }
            }

            // FMOD flags for optimal PLAYBACK mode performance
            int mode = FmodConstants.FMOD_CREATESTREAM | FmodConstants.FMOD_ACCURATETIME;

            // Create sound
            PointerByReference soundRef = new PointerByReference();
            int result = fmod.FMOD_System_CreateSound(system, canonicalPath, mode, null, soundRef);

            // Map FMOD errors to appropriate exceptions
            if (result != FmodConstants.FMOD_OK) {
                String errorMsg = fmod.FMOD_ErrorString(result);
                switch (result) {
                    case FmodConstants.FMOD_ERR_FILE_NOTFOUND:
                        throw new FileNotFoundException("FMOD cannot find file: " + canonicalPath);
                    case FmodConstants.FMOD_ERR_FORMAT:
                        throw new UnsupportedAudioFormatException(
                                "Unsupported audio format: " + canonicalPath + " - " + errorMsg);
                    case FmodConstants.FMOD_ERR_FILE_BAD:
                        throw new CorruptedAudioFileException(
                                "Corrupted audio file: " + canonicalPath + " - " + errorMsg);
                    default:
                        throw new RuntimeException(
                                "Failed to load audio file: " + canonicalPath + " - " + errorMsg);
                }
            }

            // Create handle and store as current
            long handleId = nextHandleId.getAndIncrement();
            Pointer sound = soundRef.getValue();
            FmodAudioHandle handle = new FmodAudioHandle(handleId, sound, canonicalPath);

            currentSound = sound;
            currentPath = canonicalPath;
            currentHandle = handle;

            log.info("Loaded audio file: {}", canonicalPath);
            return handle;

        } finally {
            operationLock.unlock();
        }
    }

    @Override
    public CompletableFuture<Void> preloadRange(
            @NonNull AudioHandle handle, long startFrame, long endFrame) {
        checkOperational();
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public PlaybackHandle play(@NonNull AudioHandle audio) {
        checkOperational();
        operationLock.lock();
        try {
            checkOperational(); // Double-check after acquiring lock
            throw new UnsupportedOperationException("Not yet implemented");
        } finally {
            operationLock.unlock();
        }
    }

    @Override
    public PlaybackHandle playRange(@NonNull AudioHandle audio, long startFrame, long endFrame) {
        checkOperational();
        operationLock.lock();
        try {
            checkOperational(); // Double-check after acquiring lock
            throw new UnsupportedOperationException("Not yet implemented");
        } finally {
            operationLock.unlock();
        }
    }

    @Override
    public void pause(@NonNull PlaybackHandle playback) {
        checkOperational();
        operationLock.lock();
        try {
            checkOperational();
            throw new UnsupportedOperationException("Not yet implemented");
        } finally {
            operationLock.unlock();
        }
    }

    @Override
    public void resume(@NonNull PlaybackHandle playback) {
        checkOperational();
        operationLock.lock();
        try {
            checkOperational();
            throw new UnsupportedOperationException("Not yet implemented");
        } finally {
            operationLock.unlock();
        }
    }

    @Override
    public void stop(@NonNull PlaybackHandle playback) {
        checkOperational();
        operationLock.lock();
        try {
            checkOperational();
            throw new UnsupportedOperationException("Not yet implemented");
        } finally {
            operationLock.unlock();
        }
    }

    @Override
    public void seek(@NonNull PlaybackHandle playback, long frame) {
        checkOperational();
        operationLock.lock();
        try {
            checkOperational();
            throw new UnsupportedOperationException("Not yet implemented");
        } finally {
            operationLock.unlock();
        }
    }

    @Override
    public PlaybackState getState(@NonNull PlaybackHandle playback) {
        checkOperational();
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public long getPosition(@NonNull PlaybackHandle playback) {
        checkOperational();
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public boolean isPlaying(@NonNull PlaybackHandle playback) {
        checkOperational();
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public CompletableFuture<AudioBuffer> readSamples(
            @NonNull AudioHandle audio, long startFrame, long frameCount) {
        checkOperational();
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public AudioMetadata getMetadata(@NonNull AudioHandle audio) {
        checkOperational();
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void close() {
        // Try to transition from INITIALIZED to CLOSING
        State currentState = state.get();

        // Handle each state appropriately
        switch (currentState) {
            case CLOSED:
            case CLOSING:
                return; // Already closed or closing

            case UNINITIALIZED:
                // Never initialized - just mark as closed
                state.compareAndSet(State.UNINITIALIZED, State.CLOSED);
                return;

            case INITIALIZING:
                // Try to interrupt initialization by moving to CLOSED
                // The init method will check this and cleanup
                state.compareAndSet(State.INITIALIZING, State.CLOSED);
                return;

            case INITIALIZED:
                // Normal close path
                if (!state.compareAndSet(State.INITIALIZED, State.CLOSING)) {
                    // State changed, retry
                    close();
                    return;
                }
                break;
        }

        log.info("Shutting down FMOD audio engine");

        // Now in CLOSING state - no new operations can start
        // Acquire and hold the lock for the entire cleanup to ensure
        // no operations can access resources during teardown
        operationLock.lock();
        try {
            // Stop all active playback
            if (fmod != null && !activeChannels.isEmpty()) {
                for (Map.Entry<Long, Pointer> entry : activeChannels.entrySet()) {
                    try {
                        int result = fmod.FMOD_Channel_Stop(entry.getValue());
                        if (result != FmodConstants.FMOD_OK
                                && result != FmodConstants.FMOD_ERR_BADCOMMAND) {
                            log.debug(
                                    "Error stopping channel {}: {}",
                                    entry.getKey(),
                                    fmod.FMOD_ErrorString(result));
                        }
                    } catch (Exception e) {
                        log.debug("Error stopping channel {}", entry.getKey(), e);
                    }
                }
                activeChannels.clear();
            }

            // Release current sound if any
            if (fmod != null && currentSound != null) {
                try {
                    int result = fmod.FMOD_Sound_Release(currentSound);
                    if (result != FmodConstants.FMOD_OK) {
                        log.debug("Error releasing sound: {}", fmod.FMOD_ErrorString(result));
                    }
                } catch (Exception e) {
                    log.debug("Error releasing sound", e);
                }
                currentSound = null;
                currentPath = null;
                currentHandle = null;
            }

            // Release FMOD system
            if (system != null && fmod != null) {
                int result = fmod.FMOD_System_Release(system);
                if (result != FmodConstants.FMOD_OK) {
                    log.warn("Error releasing FMOD system: {}", fmod.FMOD_ErrorString(result));
                }
            }

            // Null out references to prevent use after close
            system = null;
            fmod = null;
            config = null;

            // Transition to CLOSED
            state.set(State.CLOSED);

        } finally {
            operationLock.unlock();
        }

        log.info("FMOD audio engine shut down");
    }
}
