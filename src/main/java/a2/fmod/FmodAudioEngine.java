package a2.fmod;

import a2.AudioBuffer;
import a2.AudioEngine;
import a2.AudioEngineConfig;
import a2.AudioHandle;
import a2.AudioMetadata;
import a2.PlaybackHandle;
import a2.PlaybackState;
import app.annotations.ThreadSafe;
import audio.AudioSystemLoader;
import com.sun.jna.Library;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.FloatByReference;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/** FMOD-based implementation of AudioEngine. Uses FMOD Core API via JNA for audio operations. */
@ThreadSafe
@Slf4j
public class FmodAudioEngine implements AudioEngine {

    private FmodLibrary fmod;
    private Pointer system;
    private AudioEngineConfig config;
    private AudioEngineConfig.Mode mode;
    private final ReentrantLock lock = new ReentrantLock();
    private volatile boolean closed = false;

    // Resource management
    private final Map<Long, Pointer> soundCache = new ConcurrentHashMap<>();
    private final Map<Long, Pointer> activeChannels = new ConcurrentHashMap<>();
    private ExecutorService executor;
    private long nextHandleId = 1;
    private long nextPlaybackId = 1;

    /** FMOD Core API interface via JNA. */
    public interface FmodLibrary extends Library {
        // System creation and initialization
        int FMOD_System_Create(PointerByReference system, int headerversion);

        int FMOD_System_Init(Pointer system, int maxchannels, int flags, Pointer extradriverdata);

        int FMOD_System_Release(Pointer system);

        int FMOD_System_Update(Pointer system);

        int FMOD_System_SetOutput(Pointer system, int output);

        int FMOD_System_GetOutput(Pointer system, IntByReference output);

        int FMOD_System_SetSpeakerPosition(
                Pointer system, int speaker, float x, float y, boolean active);

        int FMOD_System_SetDSPBufferSize(Pointer system, int bufferlength, int numbuffers);

        int FMOD_System_GetDSPBufferSize(
                Pointer system, IntByReference bufferlength, IntByReference numbuffers);

        int FMOD_System_SetSoftwareFormat(
                Pointer system, int samplerate, int speakermode, int numrawspeakers);

        int FMOD_System_GetSoftwareFormat(
                Pointer system,
                IntByReference samplerate,
                IntByReference speakermode,
                IntByReference numrawspeakers);

        int FMOD_System_GetVersion(Pointer system, IntByReference version);

        int FMOD_System_GetDriverInfo(
                Pointer system,
                int id,
                byte[] name,
                int namelen,
                byte[] guid,
                IntByReference systemrate,
                IntByReference speakermode,
                IntByReference channels);

        // Sound creation and management
        int FMOD_System_CreateSound(
                Pointer system,
                String name_or_data,
                int mode,
                Pointer exinfo,
                PointerByReference sound);

        int FMOD_System_CreateStream(
                Pointer system,
                String name_or_data,
                int mode,
                Pointer exinfo,
                PointerByReference sound);

        int FMOD_Sound_Release(Pointer sound);

        int FMOD_Sound_GetLength(Pointer sound, IntByReference length, int lengthtype);

        int FMOD_Sound_GetFormat(
                Pointer sound,
                IntByReference type,
                IntByReference format,
                IntByReference channels,
                IntByReference bits);

        int FMOD_Sound_GetDefaults(
                Pointer sound, FloatByReference frequency, IntByReference priority);

        int FMOD_Sound_SetMode(Pointer sound, int mode);

        int FMOD_Sound_GetMode(Pointer sound, IntByReference mode);

        // Channel and playback control
        int FMOD_System_PlaySound(
                Pointer system,
                Pointer sound,
                Pointer channelgroup,
                boolean paused,
                PointerByReference channel);

        int FMOD_Channel_Stop(Pointer channel);

        int FMOD_Channel_SetPaused(Pointer channel, boolean paused);

        int FMOD_Channel_GetPaused(Pointer channel, IntByReference paused);

        int FMOD_Channel_SetPosition(Pointer channel, int position, int postype);

        int FMOD_Channel_GetPosition(Pointer channel, IntByReference position, int postype);

        int FMOD_Channel_IsPlaying(Pointer channel, IntByReference isplaying);

        int FMOD_Channel_SetVolume(Pointer channel, float volume);

        int FMOD_Channel_GetVolume(Pointer channel, FloatByReference volume);

        // Error handling
        String FMOD_ErrorString(int errcode);
    }

    /** Default constructor for factory use. */
    FmodAudioEngine() {}

    /** Package-private initialization method called by factory. */
    void init(@NonNull AudioEngineConfig config, @NonNull AudioSystemLoader audioSystemLoader) {
        init(config, audioSystemLoader, AudioEngineConfig.Mode.PLAYBACK);
    }

    /** Package-private initialization with explicit mode. */
    void init(
            @NonNull AudioEngineConfig config,
            @NonNull AudioSystemLoader audioSystemLoader,
            @NonNull AudioEngineConfig.Mode mode) {
        lock.lock();
        try {
            if (system != null) {
                throw new IllegalStateException("FMOD engine already initialized");
            }

            log.info("Initializing FMOD audio engine - mode: {}, config: {}", mode, config);
            this.config = config;
            this.mode = mode;

            // Create thread pool for async operations
            this.executor =
                    Executors.newFixedThreadPool(
                            config.getThreadPoolSize(),
                            r -> {
                                Thread t = new Thread(r, "FmodAudioEngine-" + System.nanoTime());
                                t.setDaemon(true);
                                return t;
                            });

            // Load FMOD library
            this.fmod = audioSystemLoader.loadAudioLibrary(FmodLibrary.class);

            // Create FMOD system
            PointerByReference systemRef = new PointerByReference();
            int result = fmod.FMOD_System_Create(systemRef, FmodConstants.FMOD_VERSION);
            checkResult(result, "Failed to create FMOD system");
            this.system = systemRef.getValue();

            // Configure based on mode
            configureForMode(mode);

            // Initialize FMOD system
            int maxChannels =
                    mode == AudioEngineConfig.Mode.PLAYBACK
                            ? 2
                            : 1; // 2 for transitions, 1 for rendering
            int initFlags = FmodConstants.FMOD_INIT_NORMAL;

            result = fmod.FMOD_System_Init(system, maxChannels, initFlags, null);
            if (result != FmodConstants.FMOD_OK) {
                fmod.FMOD_System_Release(system);
                system = null;
                checkResult(result, "Failed to initialize FMOD system");
            }

            // Log system info
            logSystemInfo();

            log.info("FMOD audio engine initialized successfully");

        } finally {
            lock.unlock();
        }
    }

    private void configureForMode(@NonNull AudioEngineConfig.Mode mode) {
        if (mode == AudioEngineConfig.Mode.PLAYBACK) {
            // Low latency configuration for playback
            // Smaller buffer for lower latency (256 samples, 4 buffers)
            int result = fmod.FMOD_System_SetDSPBufferSize(system, 256, 4);
            if (result != FmodConstants.FMOD_OK) {
                log.warn(
                        "Could not set DSP buffer size for low latency: {}",
                        fmod.FMOD_ErrorString(result));
            }

            // Set software format - mono for audio annotation app
            result =
                    fmod.FMOD_System_SetSoftwareFormat(
                            system, 48000, FmodConstants.FMOD_SPEAKERMODE_MONO, 0);
            if (result != FmodConstants.FMOD_OK) {
                log.warn("Could not set software format: {}", fmod.FMOD_ErrorString(result));
            }

        } else {
            // RENDERING mode - no audio output needed, just reading samples
            // Use NOSOUND_NRT for faster-than-realtime processing without audio device
            int result =
                    fmod.FMOD_System_SetOutput(system, FmodConstants.FMOD_OUTPUTTYPE_NOSOUND_NRT);
            if (result != FmodConstants.FMOD_OK) {
                log.warn(
                        "Could not set NOSOUND_NRT output for rendering: {}",
                        fmod.FMOD_ErrorString(result));
            }

            // Larger buffers for rendering efficiency (2048 samples, 2 buffers)
            result = fmod.FMOD_System_SetDSPBufferSize(system, 2048, 2);
            if (result != FmodConstants.FMOD_OK) {
                log.warn(
                        "Could not set DSP buffer size for rendering: {}",
                        fmod.FMOD_ErrorString(result));
            }

            // Mono format for rendering as well
            result =
                    fmod.FMOD_System_SetSoftwareFormat(
                            system, 48000, FmodConstants.FMOD_SPEAKERMODE_MONO, 0);
            if (result != FmodConstants.FMOD_OK) {
                log.warn("Could not set software format: {}", fmod.FMOD_ErrorString(result));
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

    @Override
    public AudioHandle loadAudio(@NonNull String filePath) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public CompletableFuture<Void> preloadRange(
            @NonNull AudioHandle handle, long startFrame, long endFrame) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public PlaybackHandle play(@NonNull AudioHandle audio) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public PlaybackHandle playRange(@NonNull AudioHandle audio, long startFrame, long endFrame) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void pause(@NonNull PlaybackHandle playback) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void resume(@NonNull PlaybackHandle playback) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void stop(@NonNull PlaybackHandle playback) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void seek(@NonNull PlaybackHandle playback, long frame) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public PlaybackState getState(@NonNull PlaybackHandle playback) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public long getPosition(@NonNull PlaybackHandle playback) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public boolean isPlaying(@NonNull PlaybackHandle playback) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public CompletableFuture<AudioBuffer> readSamples(
            @NonNull AudioHandle audio, long startFrame, long frameCount) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public AudioMetadata getMetadata(@NonNull AudioHandle audio) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void close() {
        lock.lock();
        try {
            if (closed) {
                return;
            }

            log.info("Shutting down FMOD audio engine");

            // Stop all active playback
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

            // Release all cached sounds
            for (Map.Entry<Long, Pointer> entry : soundCache.entrySet()) {
                try {
                    int result = fmod.FMOD_Sound_Release(entry.getValue());
                    if (result != FmodConstants.FMOD_OK) {
                        log.debug(
                                "Error releasing sound {}: {}",
                                entry.getKey(),
                                fmod.FMOD_ErrorString(result));
                    }
                } catch (Exception e) {
                    log.debug("Error releasing sound {}", entry.getKey(), e);
                }
            }
            soundCache.clear();

            // Shutdown executor
            if (executor != null) {
                executor.shutdown();
                try {
                    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                        executor.shutdownNow();
                        if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                            log.warn("Executor did not terminate in time");
                        }
                    }
                } catch (InterruptedException e) {
                    executor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }

            // Release FMOD system
            if (system != null) {
                int result = fmod.FMOD_System_Release(system);
                if (result != FmodConstants.FMOD_OK) {
                    log.warn("Error releasing FMOD system: {}", fmod.FMOD_ErrorString(result));
                }
                system = null;
            }

            closed = true;
            log.info("FMOD audio engine shut down");

        } finally {
            lock.unlock();
        }
    }
}
