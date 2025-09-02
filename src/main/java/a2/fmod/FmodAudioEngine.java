package a2.fmod;

import a2.AudioBuffer;
import a2.AudioEngine;
import a2.AudioEngineConfig;
import a2.AudioHandle;
import a2.AudioMetadata;
import a2.PlaybackHandle;
import a2.PlaybackListener;
import a2.PlaybackState;
import a2.exceptions.AudioEngineException;
import a2.exceptions.AudioLoadException;
import a2.exceptions.AudioPlaybackException;
import a2.exceptions.CorruptedAudioFileException;
import a2.exceptions.UnsupportedAudioFormatException;
import app.annotations.ThreadSafe;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalListener;
import com.google.inject.Inject;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.FloatByReference;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/** FMOD-based implementation of AudioEngine. Uses FMOD Core API via JNA for audio operations. */
@ThreadSafe
@Slf4j
public class FmodAudioEngine implements AudioEngine {

    private volatile FmodLibrary fmod;
    private volatile Pointer system;
    @Getter private volatile AudioEngineConfig config;

    private final ReentrantLock operationLock = new ReentrantLock();
    private final FmodStateManager stateManager = new FmodStateManager();

    // Resource management
    private final Map<Long, FmodPlaybackHandle> activePlaybacks = new ConcurrentHashMap<>();
    private final AtomicLong nextHandleId = new AtomicLong(1);

    // Current loaded audio (users work with one file at a time)
    private FmodAudioHandle currentHandle;
    private Pointer currentSound;
    private String currentPath;

    // Listener management
    private final List<PlaybackListener> listeners = new CopyOnWriteArrayList<>();
    private ScheduledExecutorService progressTimer;
    private static final long PROGRESS_UPDATE_INTERVAL_MS = 100;

    // Preload cache for instant replay of recent segments (e.g., last 200ms)
    private final Cache<PreloadKey, Pointer> preloadCache;

    /** Key for preload cache - identifies a specific audio segment. */
    private record PreloadKey(String filePath, long startFrame, long endFrame) {}

    /** Constructor that initializes the engine with the given configuration. */
    @Inject
    public FmodAudioEngine(@NonNull AudioEngineConfig config) {
        // Initialize preload cache with LRU eviction and automatic FMOD sound cleanup
        this.preloadCache =
                Caffeine.newBuilder()
                        .maximumSize(10)
                        .removalListener(
                                (RemovalListener<PreloadKey, Pointer>)
                                        (key, sound, _) -> {
                                            if (sound != null && fmod != null) {
                                                log.debug(
                                                        "Evicting preloaded segment: {} frames"
                                                                + " {}-{}",
                                                        key.filePath,
                                                        key.startFrame,
                                                        key.endFrame);
                                                try {
                                                    fmod.FMOD_Sound_Release(sound);
                                                } catch (Exception e) {
                                                    log.warn(
                                                            "Failed to release preloaded sound", e);
                                                }
                                            }
                                        })
                        .build();

        // Validate config before changing state
        validateConfig(config);

        // Transition from UNINITIALIZED to INITIALIZING
        if (!stateManager.compareAndSetState(
                FmodStateManager.State.UNINITIALIZED, FmodStateManager.State.INITIALIZING)) {
            FmodStateManager.State currentState = stateManager.getCurrentState();
            throw new AudioEngineException("Cannot initialize engine in state: " + currentState);
        }

        FmodLibrary fmodLib = null;
        Pointer newSystem = null;

        try {
            log.info("Initializing FMOD audio engine with config: {}", config);

            // Load FMOD library and create system (no lock needed, these are local operations)
            fmodLib = loadFmodLibrary();

            // Create FMOD system
            PointerByReference systemRef = new PointerByReference();
            int result = fmodLib.FMOD_System_Create(systemRef, FmodConstants.FMOD_VERSION);
            if (result != FmodConstants.FMOD_OK) {
                throw new AudioEngineException(
                        "Failed to create FMOD system: "
                                + " (error code: "
                                + result
                                + ")"
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
                throw new AudioEngineException(
                        "Failed to initialize FMOD system: "
                                + " (error code: "
                                + result
                                + ")"
                                + " (code: "
                                + result
                                + ")");
            }

            // Update shared state and transition to INITIALIZED
            this.system = newSystem;
            this.fmod = fmodLib;
            this.config = config;

            // Transition to INITIALIZED
            if (!stateManager.compareAndSetState(
                    FmodStateManager.State.INITIALIZING, FmodStateManager.State.INITIALIZED)) {
                // Someone called close() while we were initializing
                throw new AudioEngineException("Engine was closed during initialization");
            }

            // Log system info (after lock released)
            logSystemInfo();

            // Start progress timer for callbacks
            progressTimer =
                    Executors.newSingleThreadScheduledExecutor(
                            r -> {
                                Thread t = new Thread(r, "FmodProgressTimer");
                                t.setDaemon(true);
                                return t;
                            });
            progressTimer.scheduleAtFixedRate(
                    this::updateProgress,
                    PROGRESS_UPDATE_INTERVAL_MS,
                    PROGRESS_UPDATE_INTERVAL_MS,
                    TimeUnit.MILLISECONDS);

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
            stateManager.compareAndSetState(
                    FmodStateManager.State.INITIALIZING, FmodStateManager.State.UNINITIALIZED);

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
                        "error code: " + result);
            }

            // Set software format - mono for audio annotation app
            result =
                    fmodLib.FMOD_System_SetSoftwareFormat(
                            sys, 48000, FmodConstants.FMOD_SPEAKERMODE_MONO, 0);
            if (result != FmodConstants.FMOD_OK) {
                log.warn("Could not set software format: {}", "error code: " + result);
            }

        } else {
            // RENDERING mode - no audio output needed, just reading samples
            // Use NOSOUND_NRT for faster-than-realtime processing without audio device
            int result =
                    fmodLib.FMOD_System_SetOutput(sys, FmodConstants.FMOD_OUTPUTTYPE_NOSOUND_NRT);
            if (result != FmodConstants.FMOD_OK) {
                log.warn(
                        "Could not set NOSOUND_NRT output for rendering: {}",
                        "error code: " + result);
            }

            // Larger buffers for rendering efficiency (2048 samples, 2 buffers)
            result = fmodLib.FMOD_System_SetDSPBufferSize(sys, 2048, 2);
            if (result != FmodConstants.FMOD_OK) {
                log.warn(
                        "Could not set DSP buffer size for rendering: {}", "error code: " + result);
            }

            // Mono format for rendering as well
            result =
                    fmodLib.FMOD_System_SetSoftwareFormat(
                            sys, 48000, FmodConstants.FMOD_SPEAKERMODE_MONO, 0);
            if (result != FmodConstants.FMOD_OK) {
                log.warn("Could not set software format: {}", "error code: " + result);
            }
        }
    }

    private void logSystemInfo() {
        IntByReference version = new IntByReference();
        IntByReference buildnumber = new IntByReference();
        int result = fmod.FMOD_System_GetVersion(system, version, buildnumber);
        if (result == FmodConstants.FMOD_OK) {
            int v = version.getValue();
            log.info(
                    "FMOD version: {}.{}.{} (build {})",
                    (v >> 16) & 0xFFFF,
                    (v >> 8) & 0xFF,
                    v & 0xFF,
                    buildnumber.getValue());
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

    private void validateConfig(@NonNull AudioEngineConfig config) {
        // Validate cache size (minimum 1MB, maximum 10GB)
        long cacheBytes = config.getMaxCacheBytes();
        if (cacheBytes < 1024 * 1024) {
            throw new AudioEngineException(
                    "maxCacheBytes must be at least 1MB, got: " + cacheBytes);
        }
        if (cacheBytes > 10L * 1024 * 1024 * 1024) {
            throw new AudioEngineException(
                    "maxCacheBytes must not exceed 10GB, got: " + cacheBytes);
        }

        // Validate prefetch window (0-60 seconds)
        int prefetchSeconds = config.getPrefetchWindowSeconds();
        if (prefetchSeconds < 0) {
            throw new AudioEngineException(
                    "prefetchWindowSeconds must be non-negative, got: " + prefetchSeconds);
        }
        if (prefetchSeconds > 60) {
            throw new AudioEngineException(
                    "prefetchWindowSeconds must not exceed 60 seconds, got: " + prefetchSeconds);
        }
    }

    private FmodLibrary loadFmodLibrary() {
        // Add search path for FMOD libraries
        String resourcePath = getClass().getResource("/fmod/macos").getPath();
        NativeLibrary.addSearchPath("fmod", resourcePath);

        // Load the library
        return Native.load("fmod", FmodLibrary.class);
    }

    private void checkOperational() {
        stateManager.checkState(FmodStateManager.State.INITIALIZED);
    }

    @Override
    public AudioHandle loadAudio(@NonNull String filePath) throws AudioLoadException {
        checkOperational();

        // Validate file exists and get canonical path
        File file = new File(filePath);
        if (!file.exists()) {
            throw new AudioLoadException("Audio file not found: " + filePath);
        }
        if (!file.canRead()) {
            throw new AudioLoadException("Cannot read audio file: " + filePath);
        }

        String canonicalPath;
        try {
            canonicalPath = file.getCanonicalPath();
        } catch (IOException e) {
            throw new AudioLoadException("Cannot resolve file path: " + filePath, e);
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

                // Clear preload cache first (will trigger removal listener to release sounds)
                preloadCache.invalidateAll();

                int result = fmod.FMOD_Sound_Release(currentSound);
                if (result != FmodConstants.FMOD_OK) {
                    log.warn("Error releasing previous sound: {}", "error code: " + result);
                }
                currentSound = null;
                currentPath = null;
                currentHandle = null;

                // Ensure FMOD completes cleanup before loading new file
                result = fmod.FMOD_System_Update(system);
                if (result != FmodConstants.FMOD_OK) {
                    log.debug("Error during system update: {}", "error code: " + result);
                }
            }

            // FMOD flags for optimal PLAYBACK mode performance
            int mode = FmodConstants.FMOD_CREATESTREAM | FmodConstants.FMOD_ACCURATETIME;

            // Create sound
            PointerByReference soundRef = new PointerByReference();
            int result = fmod.FMOD_System_CreateSound(system, canonicalPath, mode, null, soundRef);

            // Map FMOD errors to appropriate exceptions
            if (result != FmodConstants.FMOD_OK) {
                String errorMsg = "error code: " + result;
                switch (result) {
                    case FmodConstants.FMOD_ERR_FILE_NOTFOUND:
                        throw new AudioLoadException("FMOD cannot find file: " + canonicalPath);
                    case FmodConstants.FMOD_ERR_FORMAT:
                        throw new UnsupportedAudioFormatException(
                                "Unsupported audio format: " + canonicalPath + " - " + errorMsg);
                    case FmodConstants.FMOD_ERR_FILE_BAD:
                        throw new CorruptedAudioFileException(
                                "Corrupted audio file: " + canonicalPath + " - " + errorMsg);
                    default:
                        throw new AudioLoadException(
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

        if (!(handle instanceof FmodAudioHandle)) {
            throw new AudioPlaybackException("Invalid audio handle type");
        }

        FmodAudioHandle fmodHandle = (FmodAudioHandle) handle;
        if (!fmodHandle.isValid()) {
            throw new AudioPlaybackException("Audio handle is no longer valid");
        }

        // Verify this is the current loaded audio
        if (currentHandle == null || currentHandle.getId() != fmodHandle.getId()) {
            throw new AudioPlaybackException("Can only preload the currently loaded file");
        }

        if (startFrame < 0 || endFrame <= startFrame) {
            throw new AudioPlaybackException(
                    "Invalid frame range: " + startFrame + " to " + endFrame);
        }

        // Run preload asynchronously to avoid blocking
        return CompletableFuture.runAsync(
                () -> {
                    PreloadKey key = new PreloadKey(currentPath, startFrame, endFrame);

                    // Check if already cached
                    if (preloadCache.getIfPresent(key) != null) {
                        log.debug("Segment already preloaded: frames {}-{}", startFrame, endFrame);
                        return;
                    }

                    operationLock.lock();
                    try {
                        // Double-check after acquiring lock
                        if (preloadCache.getIfPresent(key) != null) {
                            return;
                        }

                        // Create a separate sound object for this segment
                        // Use FMOD_CREATESAMPLE to load entire segment into memory
                        int mode =
                                FmodConstants.FMOD_CREATESAMPLE | FmodConstants.FMOD_ACCURATETIME;

                        PointerByReference soundRef = new PointerByReference();
                        int result =
                                fmod.FMOD_System_CreateSound(
                                        system, currentPath, mode, null, soundRef);

                        if (result != FmodConstants.FMOD_OK) {
                            log.warn("Failed to preload segment: {}", "error code: " + result);
                            return;
                        }

                        Pointer preloadedSound = soundRef.getValue();

                        // Store in cache (will auto-evict LRU if at capacity)
                        preloadCache.put(key, preloadedSound);
                        log.debug("Preloaded segment: frames {}-{}", startFrame, endFrame);

                    } finally {
                        operationLock.unlock();
                    }
                });
    }

    @Override
    public PlaybackHandle play(@NonNull AudioHandle audio) {
        checkOperational();
        operationLock.lock();
        try {
            checkOperational(); // Double-check after acquiring lock

            // Validate the handle
            if (!(audio instanceof FmodAudioHandle)) {
                throw new AudioPlaybackException("Invalid audio handle type");
            }

            FmodAudioHandle fmodHandle = (FmodAudioHandle) audio;
            if (!fmodHandle.isValid()) {
                throw new AudioPlaybackException("Audio handle is no longer valid");
            }

            // Verify this is the current loaded audio
            if (currentHandle == null || currentHandle.getId() != fmodHandle.getId()) {
                throw new AudioPlaybackException("Audio handle is not the currently loaded file");
            }

            // Play the sound - start paused so we can get the channel handle first
            PointerByReference channelRef = new PointerByReference();
            int result = fmod.FMOD_System_PlaySound(system, currentSound, null, true, channelRef);

            if (result != FmodConstants.FMOD_OK) {
                throw new AudioPlaybackException(
                        "Failed to play sound: " + "error code: " + result);
            }

            Pointer channel = channelRef.getValue();

            // Now unpause to start playback
            result = fmod.FMOD_Channel_SetPaused(channel, false);
            if (result != FmodConstants.FMOD_OK) {
                // Clean up the channel if we can't start it
                fmod.FMOD_Channel_Stop(channel);
                throw new AudioPlaybackException(
                        "Failed to start playback: " + "error code: " + result);
            }

            // Create playback handle
            FmodPlaybackHandle playbackHandle =
                    new FmodPlaybackHandle(audio, channel, 0, Long.MAX_VALUE);

            // Track active playback
            activePlaybacks.put(playbackHandle.getId(), playbackHandle);

            // Notify listeners
            notifyStateChanged(playbackHandle, PlaybackState.PLAYING, PlaybackState.STOPPED);

            log.debug("Started playback for file: {}", fmodHandle.getFilePath());
            return playbackHandle;
        } finally {
            operationLock.unlock();
        }
    }

    @Override
    public void playRange(@NonNull AudioHandle audio, long startFrame, long endFrame) {
        checkOperational();
        operationLock.lock();
        try {
            checkOperational(); // Double-check after acquiring lock

            // Validate the handle
            if (!(audio instanceof FmodAudioHandle)) {
                throw new AudioPlaybackException("Invalid audio handle type");
            }

            FmodAudioHandle fmodHandle = (FmodAudioHandle) audio;
            if (!fmodHandle.isValid()) {
                throw new AudioPlaybackException("Audio handle is no longer valid");
            }

            // Verify this is the current loaded audio
            if (currentHandle == null || currentHandle.getId() != fmodHandle.getId()) {
                throw new AudioPlaybackException("Audio handle is not the currently loaded file");
            }

            // Validate range
            if (startFrame < 0 || endFrame < startFrame) {
                throw new AudioPlaybackException(
                        "Invalid playback range: " + startFrame + " to " + endFrame);
            }

            // Check if this range is preloaded for instant playback
            PreloadKey key = new PreloadKey(currentPath, startFrame, endFrame);
            Pointer soundToPlay = preloadCache.getIfPresent(key);

            if (soundToPlay == null) {
                // Not preloaded, use the main streaming sound
                soundToPlay = currentSound;
                log.debug("Playing range {}-{} from streaming sound", startFrame, endFrame);
            } else {
                log.debug("Playing range {}-{} from preloaded cache", startFrame, endFrame);
            }

            // Play the sound - start paused so we can set position first
            PointerByReference channelRef = new PointerByReference();
            int result = fmod.FMOD_System_PlaySound(system, soundToPlay, null, true, channelRef);

            if (result != FmodConstants.FMOD_OK) {
                throw new AudioPlaybackException(
                        "Failed to play sound: " + "error code: " + result);
            }

            Pointer channel = channelRef.getValue();

            // If using the main streaming sound, we need to set position and loop points
            if (soundToPlay == currentSound) {
                // Set the start position (convert frames to samples/PCM position)
                if (startFrame > 0) {
                    result =
                            fmod.FMOD_Channel_SetPosition(
                                    channel, (int) startFrame, FmodConstants.FMOD_TIMEUNIT_PCM);
                    if (result != FmodConstants.FMOD_OK) {
                        fmod.FMOD_Channel_Stop(channel);
                        throw new AudioPlaybackException(
                                "Failed to set playback position: " + "error code: " + result);
                    }
                }

                // Set loop points to play from startFrame to endFrame once
                // Note: endFrame is inclusive in FMOD, so we use endFrame-1
                result =
                        fmod.FMOD_Channel_SetLoopPoints(
                                channel,
                                (int) startFrame,
                                FmodConstants.FMOD_TIMEUNIT_PCM,
                                (int) (endFrame - 1),
                                FmodConstants.FMOD_TIMEUNIT_PCM);
                if (result != FmodConstants.FMOD_OK) {
                    fmod.FMOD_Channel_Stop(channel);
                    throw new AudioPlaybackException(
                            "Failed to set loop points: " + "error code: " + result);
                }

                // Set loop count to 0 for one-shot playback (play once then stop)
                result = fmod.FMOD_Channel_SetLoopCount(channel, 0);
                if (result != FmodConstants.FMOD_OK) {
                    fmod.FMOD_Channel_Stop(channel);
                    throw new AudioPlaybackException(
                            "Failed to set loop count: " + "error code: " + result);
                }
            }
            // If using preloaded sound, it's already the exact segment we want
            // Just play it once from beginning to end

            // Now unpause to start playback
            result = fmod.FMOD_Channel_SetPaused(channel, false);
            if (result != FmodConstants.FMOD_OK) {
                fmod.FMOD_Channel_Stop(channel);
                throw new AudioPlaybackException(
                        "Failed to start playback: " + "error code: " + result);
            }

            // Fire-and-forget: channel will auto-stop at endFrame
            // No PlaybackHandle needed since it can't be controlled

            log.debug(
                    "Started range playback for file: {} from {} to {}",
                    fmodHandle.getFilePath(),
                    startFrame,
                    endFrame);
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

            // Validate the handle
            if (!(playback instanceof FmodPlaybackHandle)) {
                throw new AudioPlaybackException("Invalid playback handle type");
            }

            FmodPlaybackHandle fmodPlayback = (FmodPlaybackHandle) playback;
            if (!fmodPlayback.isActive()) {
                log.debug("Playback handle is no longer active");
                return; // Already stopped
            }

            // Check if this playback is tracked
            if (!activePlaybacks.containsKey(fmodPlayback.getId())) {
                throw new AudioPlaybackException("Unknown playback handle");
            }

            // Pause the channel
            Pointer channel = fmodPlayback.getChannel();
            int result = fmod.FMOD_Channel_SetPaused(channel, true);

            // FMOD_ERR_INVALID_HANDLE means channel already stopped
            if (result == FmodConstants.FMOD_ERR_INVALID_HANDLE) {
                log.debug("Channel already stopped");
                fmodPlayback.markInactive();
                activePlaybacks.remove(fmodPlayback.getId());
                return;
            }

            if (result != FmodConstants.FMOD_OK) {
                throw new AudioPlaybackException(
                        "Failed to pause playback: " + "error code: " + result);
            }

            // Notify listeners
            notifyStateChanged(fmodPlayback, PlaybackState.PAUSED, PlaybackState.PLAYING);

            log.debug("Paused playback {}", fmodPlayback.getId());
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

            // Validate the handle
            if (!(playback instanceof FmodPlaybackHandle)) {
                throw new AudioPlaybackException("Invalid playback handle type");
            }

            FmodPlaybackHandle fmodPlayback = (FmodPlaybackHandle) playback;
            if (!fmodPlayback.isActive()) {
                throw new AudioPlaybackException("Cannot resume inactive playback");
            }

            // Check if this playback is tracked
            if (!activePlaybacks.containsKey(fmodPlayback.getId())) {
                throw new AudioPlaybackException("Unknown playback handle");
            }

            // Resume the channel
            Pointer channel = fmodPlayback.getChannel();
            int result = fmod.FMOD_Channel_SetPaused(channel, false);

            // FMOD_ERR_INVALID_HANDLE means channel was stopped
            if (result == FmodConstants.FMOD_ERR_INVALID_HANDLE) {
                log.debug("Channel was stopped, cannot resume");
                fmodPlayback.markInactive();
                activePlaybacks.remove(fmodPlayback.getId());
                throw new AudioPlaybackException("Channel was stopped, cannot resume");
            }

            if (result != FmodConstants.FMOD_OK) {
                throw new AudioPlaybackException(
                        "Failed to resume playback: " + "error code: " + result);
            }

            // Notify listeners
            notifyStateChanged(fmodPlayback, PlaybackState.PLAYING, PlaybackState.PAUSED);

            log.debug("Resumed playback {}", fmodPlayback.getId());
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

            // Validate the handle
            if (!(playback instanceof FmodPlaybackHandle)) {
                throw new AudioPlaybackException("Invalid playback handle type");
            }

            FmodPlaybackHandle fmodPlayback = (FmodPlaybackHandle) playback;
            if (!fmodPlayback.isActive()) {
                log.debug("Playback handle is already inactive");
                return;
            }

            // Check if this playback is tracked
            if (!activePlaybacks.containsKey(fmodPlayback.getId())) {
                log.debug("Unknown playback handle");
                return;
            }

            // Stop the channel
            Pointer channel = fmodPlayback.getChannel();
            int result = fmod.FMOD_Channel_Stop(channel);

            // FMOD_ERR_INVALID_HANDLE means channel already stopped
            if (result != FmodConstants.FMOD_OK
                    && result != FmodConstants.FMOD_ERR_INVALID_HANDLE) {
                log.warn("Error stopping channel: {}", "error code: " + result);
            }

            // Mark as inactive and remove from tracking
            fmodPlayback.markInactive();
            activePlaybacks.remove(fmodPlayback.getId());

            // Notify listeners
            notifyStateChanged(fmodPlayback, PlaybackState.STOPPED, PlaybackState.PLAYING);

            log.debug("Stopped playback {}", fmodPlayback.getId());
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

            if (!(playback instanceof FmodPlaybackHandle)) {
                throw new AudioPlaybackException("Invalid playback handle type");
            }

            FmodPlaybackHandle fmodPlayback = (FmodPlaybackHandle) playback;
            if (!fmodPlayback.isActive()) {
                throw new AudioPlaybackException("Cannot seek inactive playback");
            }

            if (!activePlaybacks.containsKey(fmodPlayback.getId())) {
                throw new AudioPlaybackException("Unknown playback handle");
            }

            if (frame < 0) {
                throw new AudioPlaybackException("Invalid seek position: " + frame);
            }

            // Get current state to restore after seek
            IntByReference pausedRef = new IntByReference();
            int result = fmod.FMOD_Channel_GetPaused(fmodPlayback.getChannel(), pausedRef);
            boolean wasPaused = (result == FmodConstants.FMOD_OK && pausedRef.getValue() != 0);

            // Perform the seek
            result =
                    fmod.FMOD_Channel_SetPosition(
                            fmodPlayback.getChannel(),
                            (int) frame,
                            FmodConstants.FMOD_TIMEUNIT_PCM);

            if (result == FmodConstants.FMOD_ERR_INVALID_HANDLE) {
                fmodPlayback.markInactive();
                activePlaybacks.remove(fmodPlayback.getId());
                throw new AudioPlaybackException("Channel was stopped, cannot seek");
            }

            if (result != FmodConstants.FMOD_OK) {
                throw new AudioPlaybackException("Failed to seek: " + "error code: " + result);
            }

            // Notify listeners of state change
            PlaybackState currentState = wasPaused ? PlaybackState.PAUSED : PlaybackState.PLAYING;
            notifyStateChanged(fmodPlayback, PlaybackState.SEEKING, currentState);
            notifyStateChanged(fmodPlayback, currentState, PlaybackState.SEEKING);

            log.debug("Seeked playback {} to frame {}", fmodPlayback.getId(), frame);
        } finally {
            operationLock.unlock();
        }
    }

    @Override
    public PlaybackState getState(@NonNull PlaybackHandle playback) {
        checkOperational();

        if (!(playback instanceof FmodPlaybackHandle)) {
            throw new AudioPlaybackException("Invalid playback handle type");
        }

        FmodPlaybackHandle fmodPlayback = (FmodPlaybackHandle) playback;
        if (!fmodPlayback.isActive()) {
            return PlaybackState.STOPPED;
        }

        // Check if this playback is tracked
        if (!activePlaybacks.containsKey(fmodPlayback.getId())) {
            return PlaybackState.STOPPED;
        }

        Pointer channel = fmodPlayback.getChannel();

        // Check if channel is playing
        IntByReference isPlayingRef = new IntByReference();
        int result = fmod.FMOD_Channel_IsPlaying(channel, isPlayingRef);

        if (result == FmodConstants.FMOD_ERR_INVALID_HANDLE) {
            // Channel has stopped
            fmodPlayback.markInactive();
            activePlaybacks.remove(fmodPlayback.getId());
            return PlaybackState.STOPPED;
        }

        if (result != FmodConstants.FMOD_OK) {
            throw new AudioPlaybackException(
                    "Failed to check playback state: " + "error code: " + result);
        }

        if (isPlayingRef.getValue() == 0) {
            // Channel exists but is not playing - it has finished
            fmodPlayback.markInactive();
            activePlaybacks.remove(fmodPlayback.getId());
            return PlaybackState.STOPPED;
        }

        // Check if paused
        IntByReference isPausedRef = new IntByReference();
        result = fmod.FMOD_Channel_GetPaused(channel, isPausedRef);

        if (result != FmodConstants.FMOD_OK) {
            throw new AudioPlaybackException(
                    "Failed to check pause state: " + "error code: " + result);
        }

        return isPausedRef.getValue() != 0 ? PlaybackState.PAUSED : PlaybackState.PLAYING;
    }

    @Override
    public long getPosition(@NonNull PlaybackHandle playback) {
        checkOperational();

        if (!(playback instanceof FmodPlaybackHandle)) {
            throw new IllegalArgumentException("Invalid playback handle type");
        }

        FmodPlaybackHandle fmodPlayback = (FmodPlaybackHandle) playback;
        if (!fmodPlayback.isActive()) {
            return 0;
        }

        IntByReference positionRef = new IntByReference();
        int result =
                fmod.FMOD_Channel_GetPosition(
                        fmodPlayback.getChannel(), positionRef, FmodConstants.FMOD_TIMEUNIT_PCM);

        if (result == FmodConstants.FMOD_ERR_INVALID_HANDLE) {
            // Channel has stopped
            fmodPlayback.markInactive();
            activePlaybacks.remove(fmodPlayback.getId());
            return 0;
        }

        if (result != FmodConstants.FMOD_OK) {
            throw new AudioPlaybackException(
                    "Failed to get playback position: " + "error code: " + result);
        }

        return positionRef.getValue();
    }

    @Override
    public boolean isPlaying(@NonNull PlaybackHandle playback) {
        return getState(playback) == PlaybackState.PLAYING;
    }

    @Override
    public boolean isPaused(@NonNull PlaybackHandle playback) {
        return getState(playback) == PlaybackState.PAUSED;
    }

    @Override
    public boolean isStopped(@NonNull PlaybackHandle playback) {
        return getState(playback) == PlaybackState.STOPPED;
    }

    @Override
    public CompletableFuture<AudioBuffer> readSamples(
            @NonNull AudioHandle audio, long startFrame, long frameCount) {
        throw new AudioEngineException(
                "Reading samples is not supported in PLAYBACK mode. "
                        + "Use ANALYSIS mode for waveform visualization.");
    }

    @Override
    public AudioMetadata getMetadata(@NonNull AudioHandle audio) {
        checkOperational();

        if (!(audio instanceof FmodAudioHandle)) {
            throw new AudioPlaybackException("Invalid audio handle type");
        }

        FmodAudioHandle fmodHandle = (FmodAudioHandle) audio;
        if (!fmodHandle.isValid()) {
            throw new AudioPlaybackException("Audio handle is no longer valid");
        }

        // Verify this is the current loaded audio
        if (currentHandle == null || currentHandle.getId() != fmodHandle.getId()) {
            throw new AudioPlaybackException("Audio handle is not the currently loaded file");
        }

        operationLock.lock();
        try {
            // Get format information
            IntByReference typeRef = new IntByReference();
            IntByReference formatRef = new IntByReference();
            IntByReference channelsRef = new IntByReference();
            IntByReference bitsRef = new IntByReference();

            int result =
                    fmod.FMOD_Sound_GetFormat(
                            currentSound, typeRef, formatRef, channelsRef, bitsRef);
            if (result != FmodConstants.FMOD_OK) {
                throw new AudioEngineException(
                        "Failed to get sound format: " + "error code: " + result);
            }

            // Get sample rate
            FloatByReference frequencyRef = new FloatByReference();
            result = fmod.FMOD_Sound_GetDefaults(currentSound, frequencyRef, null);
            if (result != FmodConstants.FMOD_OK) {
                throw new AudioEngineException(
                        "Failed to get sample rate: " + "error code: " + result);
            }

            // Get length in PCM samples (frames)
            IntByReference lengthRef = new IntByReference();
            result =
                    fmod.FMOD_Sound_GetLength(
                            currentSound, lengthRef, FmodConstants.FMOD_TIMEUNIT_PCM);
            if (result != FmodConstants.FMOD_OK) {
                throw new AudioEngineException(
                        "Failed to get sound length: " + "error code: " + result);
            }

            // Extract values
            int sampleRate = Math.round(frequencyRef.getValue());
            int channelCount = channelsRef.getValue();
            int bitsPerSample = bitsRef.getValue();
            long frameCount = lengthRef.getValue();
            double durationSeconds = frameCount / (double) sampleRate;

            // Map sound type to format string
            String format = mapSoundTypeToFormat(typeRef.getValue());

            return new AudioMetadata(
                    sampleRate, channelCount, bitsPerSample, format, frameCount, durationSeconds);

        } finally {
            operationLock.unlock();
        }
    }

    private String mapSoundTypeToFormat(int soundType) {
        switch (soundType) {
            case FmodConstants.FMOD_SOUND_TYPE_WAV:
                return "WAV";
            case FmodConstants.FMOD_SOUND_TYPE_MPEG:
                return "MP3";
            case FmodConstants.FMOD_SOUND_TYPE_OGGVORBIS:
                return "OGG";
            case FmodConstants.FMOD_SOUND_TYPE_FLAC:
                return "FLAC";
            case FmodConstants.FMOD_SOUND_TYPE_AIFF:
                return "AIFF";
            case FmodConstants.FMOD_SOUND_TYPE_OPUS:
                return "OPUS";
            case FmodConstants.FMOD_SOUND_TYPE_VORBIS:
                return "VORBIS";
            case FmodConstants.FMOD_SOUND_TYPE_ASF:
                return "ASF";
            case FmodConstants.FMOD_SOUND_TYPE_RAW:
                return "RAW";
            case FmodConstants.FMOD_SOUND_TYPE_UNKNOWN:
            default:
                return "UNKNOWN";
        }
    }

    @Override
    public void addPlaybackListener(@NonNull PlaybackListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removePlaybackListener(@NonNull PlaybackListener listener) {
        listeners.remove(listener);
    }

    private void updateProgress() {
        if (listeners.isEmpty() || activePlaybacks.isEmpty()) {
            return;
        }

        for (FmodPlaybackHandle playback : activePlaybacks.values()) {
            if (!playback.isActive()) {
                continue;
            }

            try {
                IntByReference positionRef = new IntByReference();
                int result =
                        fmod.FMOD_Channel_GetPosition(
                                playback.getChannel(),
                                positionRef,
                                FmodConstants.FMOD_TIMEUNIT_PCM);

                if (result == FmodConstants.FMOD_OK) {
                    long position = positionRef.getValue();
                    long total = getAudioDuration(playback.getAudioHandle());

                    for (PlaybackListener listener : listeners) {
                        try {
                            listener.onProgress(playback, position, total);
                        } catch (Exception e) {
                            log.debug("Error in progress listener", e);
                        }
                    }
                } else if (result == FmodConstants.FMOD_ERR_INVALID_HANDLE) {
                    // Channel has stopped
                    handlePlaybackComplete(playback);
                }
            } catch (Exception e) {
                log.debug("Error updating progress for playback {}", playback.getId(), e);
            }
        }
    }

    private void handlePlaybackComplete(FmodPlaybackHandle playback) {
        playback.markInactive();
        activePlaybacks.remove(playback.getId());

        notifyStateChanged(playback, PlaybackState.FINISHED, PlaybackState.PLAYING);

        for (PlaybackListener listener : listeners) {
            try {
                listener.onPlaybackComplete(playback);
            } catch (Exception e) {
                log.debug("Error in completion listener", e);
            }
        }
    }

    private void notifyStateChanged(
            PlaybackHandle playback, PlaybackState newState, PlaybackState oldState) {
        for (PlaybackListener listener : listeners) {
            try {
                listener.onStateChanged(playback, newState, oldState);
            } catch (Exception e) {
                log.debug("Error in state change listener", e);
            }
        }
    }

    private long getAudioDuration(AudioHandle handle) {
        if (!(handle instanceof FmodAudioHandle) || currentSound == null) {
            return 0;
        }

        IntByReference lengthRef = new IntByReference();
        int result =
                fmod.FMOD_Sound_GetLength(currentSound, lengthRef, FmodConstants.FMOD_TIMEUNIT_PCM);

        return result == FmodConstants.FMOD_OK ? lengthRef.getValue() : 0;
    }

    @Override
    public void close() {
        // Try to transition from INITIALIZED to CLOSING
        FmodStateManager.State currentState = stateManager.getCurrentState();

        // Handle each state appropriately
        switch (currentState) {
            case CLOSED:
            case CLOSING:
                return; // Already closed or closing

            case UNINITIALIZED:
                // Never initialized - no-op, leave state unchanged
                log.debug("close() called on uninitialized engine - no-op");
                return;

            case INITIALIZING:
                // Try to interrupt initialization by moving to CLOSED
                // The init method will check this and cleanup
                stateManager.compareAndSetState(
                        FmodStateManager.State.INITIALIZING, FmodStateManager.State.CLOSED);
                return;

            case INITIALIZED:
                // Normal close path
                if (!stateManager.compareAndSetState(
                        FmodStateManager.State.INITIALIZED, FmodStateManager.State.CLOSING)) {
                    // State changed, retry
                    close();
                    return;
                }
                break;
        }

        log.info("Shutting down FMOD audio engine");

        // Now in CLOSING state - no new operations can start

        // Stop progress timer first
        if (progressTimer != null) {
            progressTimer.shutdown();
            try {
                if (!progressTimer.awaitTermination(1, TimeUnit.SECONDS)) {
                    progressTimer.shutdownNow();
                }
            } catch (InterruptedException e) {
                progressTimer.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // Acquire and hold the lock for the entire cleanup to ensure
        // no operations can access resources during teardown
        operationLock.lock();
        try {
            // Stop all active playback
            if (fmod != null && !activePlaybacks.isEmpty()) {
                for (Map.Entry<Long, FmodPlaybackHandle> entry : activePlaybacks.entrySet()) {
                    try {
                        FmodPlaybackHandle playback = entry.getValue();
                        int result = fmod.FMOD_Channel_Stop(playback.getChannel());
                        if (result != FmodConstants.FMOD_OK
                                && result != FmodConstants.FMOD_ERR_INVALID_HANDLE) {
                            log.debug(
                                    "Error stopping playback {}: {}",
                                    entry.getKey(),
                                    "error code: " + result);
                        }
                        playback.markInactive();
                    } catch (Exception e) {
                        log.debug("Error stopping playback {}", entry.getKey(), e);
                    }
                }
                activePlaybacks.clear();
            }

            // Clear preload cache (removal listener will release all cached sounds)
            if (preloadCache != null) {
                try {
                    preloadCache.invalidateAll();
                } catch (Exception e) {
                    log.debug("Error clearing preload cache", e);
                }
            }

            // Release current sound if any
            if (fmod != null && currentSound != null) {
                try {
                    int result = fmod.FMOD_Sound_Release(currentSound);
                    if (result != FmodConstants.FMOD_OK) {
                        log.debug("Error releasing sound: {}", "error code: " + result);
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
                    log.warn("Error releasing FMOD system: {}", "error code: " + result);
                }
            }

            // Null out references to prevent use after close
            system = null;
            fmod = null;
            config = null;

            // Transition to CLOSED
            if (!stateManager.compareAndSetState(
                    FmodStateManager.State.CLOSING, FmodStateManager.State.CLOSED)) {
                log.warn("Unexpected state during close transition");
            }

        } finally {
            operationLock.unlock();
        }

        log.info("FMOD audio engine shut down");
    }
}
