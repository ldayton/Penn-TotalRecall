package a2.fmod;

import a2.AudioBuffer;
import a2.AudioEngine;
import a2.AudioHandle;
import a2.AudioMetadata;
import a2.PlaybackHandle;
import a2.PlaybackListener;
import a2.PlaybackState;
import a2.exceptions.AudioEngineException;
import a2.exceptions.AudioLoadException;
import a2.exceptions.AudioPlaybackException;
import app.annotations.ThreadSafe;
import com.google.inject.Inject;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/** FMOD-based implementation of AudioEngine. Uses FMOD Core API via JNA for audio operations. */
@ThreadSafe
@Slf4j
public class FmodAudioEngine implements AudioEngine {

    private volatile FmodLibrary fmod;
    private volatile Pointer system;

    private final ReentrantLock operationLock = new ReentrantLock();
    private final ExecutorService readExecutor = Executors.newCachedThreadPool();
    private final FmodSystemStateManager systemStateManager = new FmodSystemStateManager();
    private FmodSystemManager systemManager;
    private FmodAudioLoadingManager loadingManager;
    private FmodPlaybackManager playbackManager;
    private FmodListenerManager listenerManager;
    private FmodSampleReader sampleReader;

    private FmodPlaybackHandle currentPlayback;
    private FmodAudioHandle currentHandle;
    private Pointer currentSound;

    @Inject
    public FmodAudioEngine() {

        if (!systemStateManager.compareAndSetState(
                FmodSystemStateManager.State.UNINITIALIZED,
                FmodSystemStateManager.State.INITIALIZING)) {
            FmodSystemStateManager.State currentState = systemStateManager.getCurrentState();
            throw new AudioEngineException("Cannot initialize engine in state: " + currentState);
        }
        try {
            log.info("Initializing FMOD audio engine");
            systemManager = new FmodSystemManager();
            systemManager.initialize();
            this.fmod = systemManager.getFmodLibrary();
            this.system = systemManager.getSystem();
            this.loadingManager = new FmodAudioLoadingManager(fmod, system, systemStateManager);
            this.playbackManager = new FmodPlaybackManager(fmod, system);
            this.listenerManager = new FmodListenerManager(fmod);
            this.sampleReader = new FmodSampleReader(fmod, system);

            if (!systemStateManager.compareAndSetState(
                    FmodSystemStateManager.State.INITIALIZING,
                    FmodSystemStateManager.State.INITIALIZED)) {
                throw new AudioEngineException("Engine was closed during initialization");
            }
            log.info("FMOD audio engine initialized successfully");

        } catch (Exception e) {
            if (systemManager != null) {
                try {
                    systemManager.shutdown();
                } catch (Exception cleanupEx) {
                    log.debug("Error during cleanup after init failure", cleanupEx);
                }
            }
            systemStateManager.compareAndSetState(
                    FmodSystemStateManager.State.INITIALIZING,
                    FmodSystemStateManager.State.UNINITIALIZED);
            throw e;
        }
    }

    private void checkOperational() {
        systemStateManager.checkState(FmodSystemStateManager.State.INITIALIZED);
    }

    @Override
    public AudioHandle loadAudio(@NonNull String filePath) throws AudioLoadException {
        checkOperational();
        operationLock.lock();
        try {
            AudioHandle handle = loadingManager.loadAudio(filePath);
            currentHandle = (FmodAudioHandle) handle;
            currentSound = loadingManager.getCurrentSound().orElse(null);
            return handle;
        } finally {
            operationLock.unlock();
        }
    }

    @Override
    public PlaybackHandle play(@NonNull AudioHandle audio) {
        checkOperational();
        operationLock.lock();
        try {
            checkOperational();
            if (!(audio instanceof FmodAudioHandle)) {
                throw new AudioPlaybackException("Invalid audio handle type");
            }
            FmodAudioHandle fmodHandle = (FmodAudioHandle) audio;
            if (!fmodHandle.isValid()) {
                throw new AudioPlaybackException("Audio handle is no longer valid");
            }
            if (currentHandle == null || currentHandle.getId() != fmodHandle.getId()) {
                throw new AudioPlaybackException("Audio handle is not the currently loaded file");
            }
            FmodPlaybackHandle playbackHandle = playbackManager.play(currentSound, audio);
            currentPlayback = playbackHandle;
            long totalFrames = getAudioDuration(audio);
            listenerManager.startMonitoring(playbackHandle, totalFrames);
            listenerManager.notifyStateChanged(
                    playbackHandle, PlaybackState.PLAYING, PlaybackState.STOPPED);
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
            checkOperational();
            if (!(audio instanceof FmodAudioHandle)) {
                throw new AudioPlaybackException("Invalid audio handle type");
            }
            FmodAudioHandle fmodHandle = (FmodAudioHandle) audio;
            if (!fmodHandle.isValid()) {
                throw new AudioPlaybackException("Audio handle is no longer valid");
            }
            if (currentHandle == null || currentHandle.getId() != fmodHandle.getId()) {
                throw new AudioPlaybackException("Audio handle is not the currently loaded file");
            }
            if (startFrame < 0 || endFrame < startFrame) {
                throw new AudioPlaybackException(
                        "Invalid playback range: " + startFrame + " to " + endFrame);
            }
            Pointer soundToPlay = currentSound;
            log.debug("Playing range {}-{} from sound", startFrame, endFrame);
            PointerByReference channelRef = new PointerByReference();
            int result = fmod.FMOD_System_PlaySound(system, soundToPlay, null, true, channelRef);

            if (result != FmodConstants.FMOD_OK) {
                throw new AudioPlaybackException(
                        "Failed to play sound: " + "error code: " + result);
            }
            Pointer channel = channelRef.getValue();

            if (soundToPlay == currentSound) {
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
                result = fmod.FMOD_Channel_SetLoopCount(channel, 0);
                if (result != FmodConstants.FMOD_OK) {
                    fmod.FMOD_Channel_Stop(channel);
                    throw new AudioPlaybackException(
                            "Failed to set loop count: " + "error code: " + result);
                }
            }

            result = fmod.FMOD_Channel_SetPaused(channel, false);
            if (result != FmodConstants.FMOD_OK) {
                fmod.FMOD_Channel_Stop(channel);
                throw new AudioPlaybackException(
                        "Failed to start playback: " + "error code: " + result);
            }
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
            if (!(playback instanceof FmodPlaybackHandle)) {
                throw new AudioPlaybackException("Invalid playback handle type");
            }
            FmodPlaybackHandle fmodPlayback = (FmodPlaybackHandle) playback;
            if (!fmodPlayback.isActive()) {
                log.debug("Playback handle is no longer active");
                return;
            }
            if (currentPlayback != fmodPlayback) {
                throw new AudioPlaybackException("Not the current playback handle");
            }
            playbackManager.pause();
            if (!playbackManager.hasActivePlayback()) {
                fmodPlayback.markInactive();
                currentPlayback = null;
                return;
            }
            listenerManager.notifyStateChanged(
                    fmodPlayback, PlaybackState.PAUSED, PlaybackState.PLAYING);
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
            if (!(playback instanceof FmodPlaybackHandle)) {
                throw new AudioPlaybackException("Invalid playback handle type");
            }
            FmodPlaybackHandle fmodPlayback = (FmodPlaybackHandle) playback;
            if (!fmodPlayback.isActive()) {
                throw new AudioPlaybackException("Cannot resume inactive playback");
            }
            if (currentPlayback != fmodPlayback) {
                throw new AudioPlaybackException("Not the current playback handle");
            }
            playbackManager.resume();
            if (!playbackManager.hasActivePlayback()) {
                log.debug("Channel was stopped, cannot resume");
                fmodPlayback.markInactive();
                currentPlayback = null;
                throw new AudioPlaybackException("Channel was stopped, cannot resume");
            }
            listenerManager.notifyStateChanged(
                    fmodPlayback, PlaybackState.PLAYING, PlaybackState.PAUSED);
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
            if (!(playback instanceof FmodPlaybackHandle)) {
                throw new AudioPlaybackException("Invalid playback handle type");
            }
            FmodPlaybackHandle fmodPlayback = (FmodPlaybackHandle) playback;
            if (!fmodPlayback.isActive()) {
                log.debug("Playback handle is already inactive");
                return;
            }
            if (currentPlayback != fmodPlayback) {
                log.debug("Not the current playback handle");
                return;
            }
            playbackManager.stop();
            fmodPlayback.markInactive();
            currentPlayback = null;
            listenerManager.stopMonitoring();
            listenerManager.notifyStateChanged(
                    fmodPlayback, PlaybackState.STOPPED, PlaybackState.PLAYING);
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
            if (currentPlayback != fmodPlayback) {
                throw new AudioPlaybackException("Not the current playback handle");
            }
            if (frame < 0) {
                throw new AudioPlaybackException("Invalid seek position: " + frame);
            }
            IntByReference pausedRef = new IntByReference();
            int result = fmod.FMOD_Channel_GetPaused(fmodPlayback.getChannel(), pausedRef);
            boolean wasPaused = (result == FmodConstants.FMOD_OK && pausedRef.getValue() != 0);
            playbackManager.seek(frame);
            if (!playbackManager.hasActivePlayback()) {
                fmodPlayback.markInactive();
                currentPlayback = null;
                throw new AudioPlaybackException("Channel was stopped, cannot seek");
            }
            PlaybackState currentState = wasPaused ? PlaybackState.PAUSED : PlaybackState.PLAYING;
            listenerManager.notifyStateChanged(fmodPlayback, PlaybackState.SEEKING, currentState);
            listenerManager.notifyStateChanged(fmodPlayback, currentState, PlaybackState.SEEKING);
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
        if (currentPlayback != fmodPlayback) {
            return PlaybackState.STOPPED;
        }
        Pointer channel = fmodPlayback.getChannel();
        IntByReference isPlayingRef = new IntByReference();
        int result = fmod.FMOD_Channel_IsPlaying(channel, isPlayingRef);
        if (result == FmodConstants.FMOD_ERR_INVALID_HANDLE) {
            fmodPlayback.markInactive();
            currentPlayback = null;
            return PlaybackState.STOPPED;
        }
        if (result != FmodConstants.FMOD_OK) {
            throw new AudioPlaybackException(
                    "Failed to check playback state: " + "error code: " + result);
        }
        if (isPlayingRef.getValue() == 0) {
            fmodPlayback.markInactive();
            currentPlayback = null;
            return PlaybackState.STOPPED;
        }
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
        long position = playbackManager.getPosition();
        if (position == 0 && !playbackManager.hasActivePlayback()) {
            fmodPlayback.markInactive();
            currentPlayback = null;
        }
        return position;
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
        checkOperational();

        if (!(audio instanceof FmodAudioHandle)) {
            return CompletableFuture.failedFuture(
                    new AudioEngineException("Invalid audio handle type"));
        }

        FmodAudioHandle fmodHandle = (FmodAudioHandle) audio;
        if (!fmodHandle.isValid() || !loadingManager.isCurrent(audio)) {
            return CompletableFuture.failedFuture(
                    new AudioEngineException("Audio handle is not the currently loaded file"));
        }

        if (startFrame < 0 || frameCount <= 0) {
            return CompletableFuture.failedFuture(
                    new AudioEngineException(
                            "Invalid frame range: start=" + startFrame + ", count=" + frameCount));
        }

        String filePath = fmodHandle.getFilePath();

        return CompletableFuture.supplyAsync(
                () -> sampleReader.readSamples(filePath, startFrame, frameCount), readExecutor);
    }

    @Override
    public AudioMetadata getMetadata(@NonNull AudioHandle audio) {
        checkOperational();
        if (!loadingManager.isCurrent(audio)) {
            throw new AudioPlaybackException("Audio handle is not the currently loaded file");
        }
        return loadingManager
                .getCurrentMetadata()
                .orElseThrow(
                        () ->
                                new AudioPlaybackException(
                                        "No metadata available for current audio"));
    }

    @Override
    public void addPlaybackListener(@NonNull PlaybackListener listener) {
        listenerManager.addListener(listener);
    }

    @Override
    public void removePlaybackListener(@NonNull PlaybackListener listener) {
        listenerManager.removeListener(listener);
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
        FmodSystemStateManager.State currentState = systemStateManager.getCurrentState();
        switch (currentState) {
            case CLOSED:
            case CLOSING:
                return;
            case UNINITIALIZED:
                log.debug("close() called on uninitialized engine - no-op");
                return;
            case INITIALIZING:
                systemStateManager.compareAndSetState(
                        FmodSystemStateManager.State.INITIALIZING,
                        FmodSystemStateManager.State.CLOSED);
                return;
            case INITIALIZED:
                if (!systemStateManager.compareAndSetState(
                        FmodSystemStateManager.State.INITIALIZED,
                        FmodSystemStateManager.State.CLOSING)) {
                    close();
                    return;
                }
                break;
        }

        log.info("Shutting down FMOD audio engine");
        operationLock.lock();
        try {
            if (fmod != null && currentPlayback != null) {
                try {
                    int result = fmod.FMOD_Channel_Stop(currentPlayback.getChannel());
                    if (result != FmodConstants.FMOD_OK
                            && result != FmodConstants.FMOD_ERR_INVALID_HANDLE) {
                        log.debug("Error stopping playback: {}", "error code: " + result);
                    }
                    currentPlayback.markInactive();
                } catch (Exception e) {
                    log.debug("Error stopping playback", e);
                }
                currentPlayback = null;
            }

            if (listenerManager != null) {
                try {
                    listenerManager.shutdown();
                } catch (Exception e) {
                    log.debug("Error shutting down listener manager", e);
                }
            }
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
                currentHandle = null;
            }

            if (readExecutor != null) {
                readExecutor.shutdown();
            }
            if (systemManager != null) {
                systemManager.shutdown();
            }
            system = null;
            fmod = null;
            systemManager = null;
            playbackManager = null;
            listenerManager = null;
            sampleReader = null;
            if (!systemStateManager.compareAndSetState(
                    FmodSystemStateManager.State.CLOSING, FmodSystemStateManager.State.CLOSED)) {
                log.warn("Unexpected state during close transition");
            }
        } finally {
            operationLock.unlock();
        }
        log.info("FMOD audio engine shut down");
    }
}
