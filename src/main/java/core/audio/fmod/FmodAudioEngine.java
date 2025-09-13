package core.audio.fmod;

import com.google.errorprone.annotations.ThreadSafe;
import com.google.inject.Inject;
import core.audio.AudioEngine;
import core.audio.AudioHandle;
import core.audio.AudioMetadata;
import core.audio.PlaybackHandle;
import core.audio.PlaybackListener;
import core.audio.PlaybackState;
import core.audio.exceptions.AudioEngineException;
import core.audio.exceptions.AudioLoadException;
import core.audio.exceptions.AudioPlaybackException;
import core.audio.fmod.panama.FmodCore;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.concurrent.locks.ReentrantLock;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/** FMOD-based implementation of AudioEngine. Uses FMOD Core API via JNA for audio operations. */
@ThreadSafe
@Slf4j
public class FmodAudioEngine implements AudioEngine {

    private final ReentrantLock operationLock = new ReentrantLock();

    // Injected dependencies
    private final FmodSystemManager systemManager;
    private final FmodAudioLoadingManager loadingManager;
    private final FmodPlaybackManager playbackManager;
    private final FmodListenerManager listenerManager;
    private final FmodSystemStateManager systemStateManager;
    private final FmodHandleLifecycleManager lifecycleManager;

    // Runtime state
    private FmodPlaybackHandle currentPlayback;
    private MemorySegment currentSound;

    @Inject
    public FmodAudioEngine(
            @NonNull FmodSystemManager systemManager,
            @NonNull FmodAudioLoadingManager loadingManager,
            @NonNull FmodPlaybackManager playbackManager,
            @NonNull FmodListenerManager listenerManager,
            @NonNull FmodSystemStateManager systemStateManager,
            @NonNull FmodHandleLifecycleManager lifecycleManager) {

        this.systemManager = systemManager;
        this.loadingManager = loadingManager;
        this.playbackManager = playbackManager;
        this.listenerManager = listenerManager;
        this.systemStateManager = systemStateManager;
        this.lifecycleManager = lifecycleManager;

        if (!systemStateManager.compareAndSetState(
                FmodSystemStateManager.State.UNINITIALIZED,
                FmodSystemStateManager.State.INITIALIZING)) {
            FmodSystemStateManager.State currentState = systemStateManager.getCurrentState();
            throw new AudioEngineException("Cannot initialize engine in state: " + currentState);
        }
        try {

            // Initialize the system if needed
            if (!systemManager.isInitialized()) {
                systemManager.initialize();
            }

            if (!systemStateManager.compareAndSetState(
                    FmodSystemStateManager.State.INITIALIZING,
                    FmodSystemStateManager.State.INITIALIZED)) {
                throw new AudioEngineException("Engine was closed during initialization");
            }

        } catch (Exception e) {
            if (systemManager != null) {
                try {
                    systemManager.shutdown();
                } catch (Exception cleanupEx) {
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
            currentSound = loadingManager.getCurrentSound().orElse(null);
            return handle;
        } finally {
            operationLock.unlock();
        }
    }

    @Override
    public PlaybackHandle play(@NonNull AudioHandle audio) {
        AudioMetadata metadata = getMetadata(audio);
        return playInternal(audio, 0, metadata.frameCount());
    }

    @Override
    public PlaybackHandle play(@NonNull AudioHandle audio, long startFrame, long endFrame) {
        return playInternal(audio, startFrame, endFrame);
    }

    /**
     * Internal unified play implementation used by both play() methods.
     *
     * @param audio The audio handle to play
     * @param startFrame Starting frame (0 for beginning)
     * @param endFrame Ending frame (inclusive)
     * @return PlaybackHandle for the playback
     */
    private PlaybackHandle playInternal(
            @NonNull AudioHandle audio, long startFrame, long endFrame) {
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
            if (!lifecycleManager.isCurrent(fmodHandle)) {
                throw new AudioPlaybackException("Audio handle is not the currently loaded file");
            }

            // Validate range
            if (startFrame < 0 || endFrame < startFrame) {
                throw new AudioPlaybackException(
                        "Invalid playback range: " + startFrame + " to " + endFrame);
            }

            AudioMetadata metadata = getMetadata(audio);
            boolean isRangePlayback = startFrame > 0 || endFrame < metadata.frameCount();

            // Check for existing playback
            if (currentPlayback != null && currentPlayback.isActive()) {
                if (isRangePlayback) {
                    // Range playback stops current playback
                    playbackManager.stop();
                    currentPlayback.markInactive();
                    listenerManager.stopMonitoring();
                    listenerManager.notifyStateChanged(
                            currentPlayback, PlaybackState.STOPPED, PlaybackState.PLAYING);
                    currentPlayback = null;
                } else {
                    // Full playback enforces single playback restriction
                    throw new AudioPlaybackException("Another playback is already active");
                }
            }

            // For range playback, we need to create the channel manually to configure it
            // For full playback, use the normal playbackManager
            FmodPlaybackHandle playbackHandle;

            if (startFrame > 0 || endFrame < metadata.frameCount()) {
                // Range playback - use playbackManager's playRange method
                boolean notifyListeners = true;
                playbackHandle =
                        playbackManager.playRange(
                                currentSound, audio, startFrame, endFrame, notifyListeners);
            } else {
                // Full playback - use normal playbackManager
                playbackHandle = playbackManager.play(currentSound, audio);
            }

            currentPlayback = playbackHandle;
            long durationFrames = endFrame - startFrame;
            listenerManager.startMonitoring(playbackHandle, durationFrames);
            listenerManager.notifyStateChanged(
                    playbackHandle, PlaybackState.PLAYING, PlaybackState.STOPPED);
            return playbackHandle;
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
                fmodPlayback.markInactive();
                currentPlayback = null;
                throw new AudioPlaybackException("Channel was stopped, cannot resume");
            }
            listenerManager.notifyStateChanged(
                    fmodPlayback, PlaybackState.PLAYING, PlaybackState.PAUSED);
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
                return;
            }
            if (currentPlayback != fmodPlayback) {
                return;
            }
            playbackManager.stop();
            fmodPlayback.markInactive();
            currentPlayback = null;
            listenerManager.stopMonitoring();
            listenerManager.notifyStateChanged(
                    fmodPlayback, PlaybackState.STOPPED, PlaybackState.PLAYING);
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
            try (Arena arena = Arena.ofConfined()) {
                var pausedRef = arena.allocate(ValueLayout.JAVA_INT);
                int result = FmodCore.FMOD_Channel_GetPaused(fmodPlayback.getChannel(), pausedRef);
                boolean wasPaused =
                        (result == FmodConstants.FMOD_OK
                                && pausedRef.get(ValueLayout.JAVA_INT, 0) != 0);
                playbackManager.seek(frame);
                // Update hearing-time baseline to reflect new absolute position
                listenerManager.onSeek(fmodPlayback, frame);
                if (!playbackManager.hasActivePlayback()) {
                    fmodPlayback.markInactive();
                    currentPlayback = null;
                    throw new AudioPlaybackException("Channel was stopped, cannot seek");
                }
                PlaybackState currentState =
                        wasPaused ? PlaybackState.PAUSED : PlaybackState.PLAYING;
                listenerManager.notifyStateChanged(
                        fmodPlayback, PlaybackState.SEEKING, currentState);
                listenerManager.notifyStateChanged(
                        fmodPlayback, currentState, PlaybackState.SEEKING);
            }
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
        MemorySegment channel = fmodPlayback.getChannel();
        try (Arena arena = Arena.ofConfined()) {
            var isPlayingRef = arena.allocate(ValueLayout.JAVA_INT);
            int result = FmodCore.FMOD_Channel_IsPlaying(channel, isPlayingRef);
            if (result == FmodConstants.FMOD_ERR_INVALID_HANDLE
                    || result == FmodConstants.FMOD_ERR_CHANNEL_STOLEN) {
                fmodPlayback.markInactive();
                currentPlayback = null;
                return PlaybackState.STOPPED;
            }
            if (result != FmodConstants.FMOD_OK) {
                throw new AudioPlaybackException(
                        "Failed to check playback state: " + "error code: " + result);
            }
            if (isPlayingRef.get(ValueLayout.JAVA_INT, 0) == 0) {
                fmodPlayback.markInactive();
                currentPlayback = null;
                return PlaybackState.STOPPED;
            }
            var isPausedRef = arena.allocate(ValueLayout.JAVA_INT);
            result = FmodCore.FMOD_Channel_GetPaused(channel, isPausedRef);
            if (result != FmodConstants.FMOD_OK) {
                throw new AudioPlaybackException(
                        "Failed to check pause state: " + "error code: " + result);
            }
            return isPausedRef.get(ValueLayout.JAVA_INT, 0) != 0
                    ? PlaybackState.PAUSED
                    : PlaybackState.PLAYING;
        }
    }

    @Override
    public double getHearingSeconds(@NonNull PlaybackHandle playback) {
        checkOperational();
        if (!(playback instanceof FmodPlaybackHandle)) {
            throw new IllegalArgumentException("Invalid playback handle type");
        }
        FmodPlaybackHandle fmodPlayback = (FmodPlaybackHandle) playback;
        if (!fmodPlayback.isActive()) {
            return 0.0;
        }
        // Return latest hearing-time from listener manager, clamped to audio length
        double sec = listenerManager.getCurrentHearingSeconds();
        try {
            AudioMetadata md = getMetadata(fmodPlayback.getAudioHandle());
            double lengthSec = md.frameCount() / (double) md.sampleRate();
            if (lengthSec > 0) {
                sec = Math.max(0.0, Math.min(lengthSec, sec));
            }
        } catch (Exception ignored) {
        }
        return sec;
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

    @Override
    public void close() {
        FmodSystemStateManager.State currentState = systemStateManager.getCurrentState();
        switch (currentState) {
            case CLOSED:
            case CLOSING:
                return;
            case UNINITIALIZED:
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

        operationLock.lock();
        try {
            if (currentPlayback != null) {
                try {
                    int result = FmodCore.FMOD_Channel_Stop(currentPlayback.getChannel());
                    if (result != FmodConstants.FMOD_OK
                            && result != FmodConstants.FMOD_ERR_INVALID_HANDLE) {}
                    currentPlayback.markInactive();
                } catch (Exception e) {
                }
                currentPlayback = null;
            }

            if (listenerManager != null) {
                try {
                    listenerManager.shutdown();
                } catch (Exception e) {
                }
            }
            if (currentSound != null) {
                try {
                    int result = FmodCore.FMOD_Sound_Release(currentSound);
                    if (result != FmodConstants.FMOD_OK) {}
                } catch (Exception e) {
                }
                currentSound = null;
            }

            if (systemManager != null) {
                systemManager.shutdown();
            }
            if (!systemStateManager.compareAndSetState(
                    FmodSystemStateManager.State.CLOSING, FmodSystemStateManager.State.CLOSED)) {
                log.warn("Unexpected state during close transition");
            }
        } finally {
            operationLock.unlock();
        }
    }
}
