package core.audio;

import com.google.inject.Provider;
import core.dispatch.EventDispatchBus;
import core.dispatch.Subscribe;
import core.events.AppStateChangedEvent;
import core.events.AudioFileLoadRequestedEvent;
import core.events.CloseAudioFileEvent;
import core.events.PlayLast200MillisEvent;
import core.events.PlayLast200MillisThenMoveEvent;
import core.events.PlayPauseEvent;
import core.events.SeekByAmountEvent;
import core.events.SeekEvent;
import core.events.SeekToStartEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.File;
import java.util.Optional;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages the current audio session: tracks state transitions, delegates to the audio engine, and
 * publishes state change events. Maintains only essential state like current file and handles.
 */
@Singleton
@Slf4j
public class AudioSessionManager implements AudioSessionDataSource {

    private final AudioSessionStateMachine stateManager;
    private final Provider<AudioEngine> audioEngineProvider;
    private final EventDispatchBus eventBus;

    private Optional<AudioEngine> audioEngine = Optional.empty();
    private Optional<File> currentFile = Optional.empty();
    private Optional<AudioHandle> currentAudioHandle = Optional.empty();
    private Optional<PlaybackHandle> currentPlaybackHandle = Optional.empty();
    private String errorMessage = null;

    // Audio metadata
    private volatile long totalFrames = 0;
    private volatile int sampleRate = 0;

    @Inject
    public AudioSessionManager(
            @NonNull AudioSessionStateMachine stateManager,
            @NonNull Provider<AudioEngine> audioEngineProvider,
            @NonNull EventDispatchBus eventBus) {
        this.stateManager = stateManager;
        this.audioEngineProvider = audioEngineProvider;
        this.eventBus = eventBus;
        eventBus.subscribe(this);
    }

    // Event subscriptions (UI -> AppManager)

    @Subscribe
    public void onAudioFileLoadRequested(@NonNull AudioFileLoadRequestedEvent event) {
        log.debug("Loading audio file: {}", event.getFile().getAbsolutePath());

        // If currently playing or paused, stop first and transition to READY
        var previousState = stateManager.getCurrentState();
        if (previousState == AudioSessionStateMachine.State.PLAYING
                || previousState == AudioSessionStateMachine.State.PAUSED) {
            stopPlayback(); // This transitions to READY
        }

        // Close current file if any
        closeCurrentSession();

        // Transition to loading state
        stateManager.transitionToLoading();
        currentFile = Optional.of(event.getFile());
        eventBus.publish(
                new AppStateChangedEvent(
                        previousState, AudioSessionStateMachine.State.LOADING, event.getFile()));

        // Initialize audio engine if needed
        if (audioEngine.isEmpty()) {
            var engine = audioEngineProvider.get();
            audioEngine = Optional.of(engine);
        }

        // Load the audio file
        try {
            var handle = audioEngine.get().loadAudio(event.getFile().getAbsolutePath());
            currentAudioHandle = Optional.of(handle);

            // Cache metadata for efficient access
            var metadata = audioEngine.get().getMetadata(handle);
            this.sampleRate = metadata.sampleRate();
            this.totalFrames = metadata.frameCount();

            var prevState = stateManager.getCurrentState();
            stateManager.transitionToReady();
            eventBus.publish(
                    new AppStateChangedEvent(
                            prevState, AudioSessionStateMachine.State.READY, event.getFile()));
            log.info("Audio file loaded successfully: {}", event.getFile().getName());
        } catch (Exception e) {
            errorMessage = e.getMessage();
            var prevState = stateManager.getCurrentState();
            stateManager.transitionToError();
            eventBus.publish(
                    new AppStateChangedEvent(prevState, AudioSessionStateMachine.State.ERROR, e));
            log.error("Failed to load audio file: {}", event.getFile().getAbsolutePath(), e);
        }
    }

    @Subscribe
    public void onAudioPlayPauseRequested(@NonNull PlayPauseEvent event) {
        var state = stateManager.getCurrentState();

        switch (state) {
            case READY -> {
                // Start playback from current cached frame (avoids glitch after READY seeks)
                currentAudioHandle.ifPresent(
                        handle -> {
                            // When playing, start from position 0 (will be overridden if seeking in
                            // READY)
                            long startFrame = 0;
                            long endFrame = totalFrames; // exclusive upper bound
                            var playback = audioEngine.get().play(handle, startFrame, endFrame);
                            currentPlaybackHandle = Optional.of(playback);
                            var prevState = stateManager.getCurrentState();
                            stateManager.transitionToPlaying();
                            var positionFrames = audioEngine.get().getPosition(playback);
                            var position =
                                    sampleRate > 0 ? (double) positionFrames / sampleRate : 0.0;
                            eventBus.publish(
                                    new AppStateChangedEvent(
                                            prevState,
                                            AudioSessionStateMachine.State.PLAYING,
                                            position));
                            log.debug("Started playback from frame {}", startFrame);
                        });
            }
            case PLAYING -> {
                // Pause playback
                currentPlaybackHandle.ifPresent(
                        playback -> {
                            audioEngine.get().pause(playback);
                            var prevState = stateManager.getCurrentState();
                            stateManager.transitionToPaused();
                            var positionFrames = audioEngine.get().getPosition(playback);
                            var position =
                                    sampleRate > 0 ? (double) positionFrames / sampleRate : 0.0;
                            eventBus.publish(
                                    new AppStateChangedEvent(
                                            prevState,
                                            AudioSessionStateMachine.State.PAUSED,
                                            position));
                            log.debug("Paused playback");
                        });
            }
            case PAUSED -> {
                // Resume playback
                currentPlaybackHandle.ifPresent(
                        playback -> {
                            audioEngine.get().resume(playback);
                            var prevState = stateManager.getCurrentState();
                            stateManager.transitionToPlaying();
                            var positionFrames = audioEngine.get().getPosition(playback);
                            var position =
                                    sampleRate > 0 ? (double) positionFrames / sampleRate : 0.0;
                            eventBus.publish(
                                    new AppStateChangedEvent(
                                            prevState,
                                            AudioSessionStateMachine.State.PLAYING,
                                            position));
                            log.debug("Resumed playback");
                        });
            }
            default -> log.warn("Play/pause requested in invalid state: {}", state);
        }
    }

    @Subscribe
    public void fileclose(@NonNull CloseAudioFileEvent event) {
        log.debug("Closing audio file");
        var fileBeingClosed = currentFile.orElse(null);
        var prevState = stateManager.getCurrentState();
        closeCurrentSession();
        stateManager.transitionToNoAudio();
        eventBus.publish(
                new AppStateChangedEvent(
                        prevState, AudioSessionStateMachine.State.NO_AUDIO, fileBeingClosed));
    }

    @Subscribe
    public void onSeekByAmountRequested(@NonNull SeekByAmountEvent event) {
        // Get the actual current position from FMOD instead of using cached value
        long actualPosition = 0;
        if (currentPlaybackHandle.isPresent() && audioEngine.isPresent()) {
            // Get current position in frames for seek-by-amount baseline
            actualPosition = audioEngine.get().getPosition(currentPlaybackHandle.get());
        } else {
            // No active playback, use position 0
            actualPosition = 0;
        }

        // Calculate target frame based on actual position and requested amount
        long shiftFrames = (long) ((event.milliseconds() / 1000.0) * sampleRate);
        long targetFrame =
                event.direction() == SeekByAmountEvent.Direction.FORWARD
                        ? actualPosition + shiftFrames
                        : actualPosition - shiftFrames;

        // Ensure within bounds
        targetFrame = Math.max(0, Math.min(targetFrame, totalFrames - 1));

        // Delegate to existing seek logic
        onAudioSeekRequested(new SeekEvent(targetFrame));
    }

    @Subscribe
    public void onAudioSeekRequested(@NonNull SeekEvent event) {
        var state = stateManager.getCurrentState();

        if (currentPlaybackHandle.isPresent()) {
            // If we have a playback handle (playing or paused), just seek it
            audioEngine.ifPresent(
                    engine -> {
                        engine.seek(currentPlaybackHandle.get(), event.frame());
                        // Don't update currentPositionFrames here - let onProgress handle it
                        log.debug("Seeked to frame: {}", event.frame());
                    });
        } else if (state == AudioSessionStateMachine.State.READY
                && currentAudioHandle.isPresent()) {
            // In READY state, we can't actually seek since there's no playback.
            // The seek position will need to be handled when playback starts.
            log.debug("Cannot seek in READY state - no active playback");
        }
    }

    @Subscribe
    public void onAudioStopRequested(@NonNull SeekToStartEvent event) {
        var state = stateManager.getCurrentState();

        if (state == AudioSessionStateMachine.State.PLAYING
                || state == AudioSessionStateMachine.State.PAUSED) {
            // Stop playback and reset position to beginning
            stopPlayback();

            // Ensure we're in READY state even if stopPlayback didn't transition
            // (e.g., if playback handle was already invalid)
            var currentState = stateManager.getCurrentState();
            if (currentState == AudioSessionStateMachine.State.PLAYING
                    || currentState == AudioSessionStateMachine.State.PAUSED) {
                stateManager.transitionToReady();
                eventBus.publish(
                        new AppStateChangedEvent(
                                currentState, AudioSessionStateMachine.State.READY, "stopped"));
                log.debug("Forced transition to READY after SeekToStart with invalid handle");
            }
            log.debug("Stopped playback and reset to beginning");
        } else if (state == AudioSessionStateMachine.State.READY) {
            // Already stopped
            log.debug("Already at beginning (READY state)");
        }
    }

    @Subscribe
    public void onReplayLast200MillisRequested(@NonNull PlayLast200MillisEvent event) {
        var state = stateManager.getCurrentState();

        if ((state == AudioSessionStateMachine.State.READY
                        || state == AudioSessionStateMachine.State.PAUSED)
                && currentAudioHandle.isPresent()) {

            // Get current position
            long currentFrame = 0;
            if (currentPlaybackHandle.isPresent()) {
                currentFrame = audioEngine.get().getPosition(currentPlaybackHandle.get());
            }

            // Calculate 200ms in frames
            int currentSampleRate =
                    this.sampleRate != 0 ? this.sampleRate : 44100; // Use default if not set
            long framesToReplay = (long) (currentSampleRate * 0.2); // 200ms
            long startFrame = Math.max(0, currentFrame - framesToReplay);
            long endFrame = currentFrame;

            // Only play if there's actually something to replay (endFrame > startFrame)
            if (endFrame > startFrame) {
                audioEngine.get().play(currentAudioHandle.get(), startFrame, endFrame);
            }

            log.debug("Replaying last 200ms from frame {} to {}", startFrame, endFrame);
        }
    }

    @Subscribe
    public void onLast200PlusMoveRequested(@NonNull PlayLast200MillisThenMoveEvent event) {
        var state = stateManager.getCurrentState();

        if ((state == AudioSessionStateMachine.State.READY
                        || state == AudioSessionStateMachine.State.PAUSED)
                && currentPlaybackHandle.isPresent()
                && currentAudioHandle.isPresent()) {

            // Get current position
            long currentFrame = audioEngine.get().getPosition(currentPlaybackHandle.get());

            // Calculate shift (200ms)
            int currentSampleRate = this.sampleRate != 0 ? this.sampleRate : 44100;
            long shiftFrames = (long) (currentSampleRate * 0.2); // 200ms shift

            // Calculate new position based on direction
            long newFrame =
                    event.forward() ? currentFrame + shiftFrames : currentFrame - shiftFrames;

            // Ensure we don't go out of bounds
            newFrame = Math.max(0, Math.min(newFrame, totalFrames - 1));

            // Seek to the new position
            audioEngine.get().seek(currentPlaybackHandle.get(), newFrame);

            // Now replay the last 200ms from this new position
            long replayStartFrame = Math.max(0, newFrame - shiftFrames);
            long replayEndFrame = newFrame;

            // Play the interval
            audioEngine.get().play(currentAudioHandle.get(), replayStartFrame, replayEndFrame);

            log.debug(
                    "Moved {} to frame {} and replaying from {} to {}",
                    event.forward() ? "forward" : "backward",
                    newFrame,
                    replayStartFrame,
                    replayEndFrame);
        }
    }

    // WaveformSessionSource implementation

    @Override
    public Optional<Double> getPlaybackPosition() {
        if (currentAudioHandle.isEmpty() || sampleRate == 0) {
            return Optional.empty();
        }
        // Get actual position from FMOD
        if (currentPlaybackHandle.isPresent() && audioEngine.isPresent()) {
            long posFrames = audioEngine.get().getPosition(currentPlaybackHandle.get());
            double posSec = sampleRate > 0 ? (double) posFrames / sampleRate : 0.0;
            return Optional.of(posSec);
        }
        // No active playback, position is 0
        return Optional.of(0.0);
    }

    @Override
    public Optional<Double> getTotalDuration() {
        if (currentAudioHandle.isEmpty() || sampleRate == 0) {
            return Optional.empty();
        }
        // Use cached values for efficiency
        return Optional.of((double) totalFrames / sampleRate);
    }

    @Override
    public boolean isAudioLoaded() {
        return stateManager.isAudioLoaded();
    }

    @Override
    public boolean isPlaying() {
        return stateManager.getCurrentState() == AudioSessionStateMachine.State.PLAYING;
    }

    @Override
    public boolean isLoading() {
        return stateManager.getCurrentState() == AudioSessionStateMachine.State.LOADING;
    }

    @Override
    public Optional<String> getErrorMessage() {
        return Optional.ofNullable(errorMessage);
    }

    @Override
    public Optional<AudioHandle> getCurrentAudioHandle() {
        return currentAudioHandle;
    }

    @Override
    public Optional<String> getCurrentAudioFilePath() {
        return currentFile.map(f -> f.getAbsolutePath());
    }

    @Override
    public Optional<Integer> getSampleRate() {
        return sampleRate > 0 ? Optional.of(sampleRate) : Optional.empty();
    }

    @Override
    public Optional<Long> getPlaybackPositionFrames() {
        if (currentAudioHandle.isEmpty()) {
            return Optional.empty();
        }
        // Get actual position from FMOD if we have an active playback
        if (currentPlaybackHandle.isPresent() && audioEngine.isPresent()) {
            long frames = audioEngine.get().getPosition(currentPlaybackHandle.get());
            return Optional.of(frames);
        }
        // No active playback, position is 0
        return Optional.of(0L);
    }

    @Override
    public Optional<Long> getTotalFrames() {
        if (currentAudioHandle.isEmpty() || totalFrames == 0) {
            return Optional.empty();
        }
        return Optional.of(totalFrames);
    }

    // Public for testing
    public Optional<PlaybackHandle> getCurrentPlaybackHandle() {
        return currentPlaybackHandle;
    }

    /**
     * Stop playback and transition to READY state. Used when user explicitly stops playback (not
     * pause).
     */
    public void stopPlayback() {
        if (currentPlaybackHandle.isPresent()) {
            var prevState = stateManager.getCurrentState();
            audioEngine.get().stop(currentPlaybackHandle.get());
            currentPlaybackHandle.get().close();
            currentPlaybackHandle = Optional.empty();

            if (prevState == AudioSessionStateMachine.State.PLAYING
                    || prevState == AudioSessionStateMachine.State.PAUSED) {
                stateManager.transitionToReady();
                eventBus.publish(
                        new AppStateChangedEvent(
                                prevState, AudioSessionStateMachine.State.READY, "stopped"));
                log.debug("Playback stopped");
            }
        }
    }

    private void closeCurrentSession() {
        // Stop any active playback
        if (currentPlaybackHandle.isPresent() && audioEngine.isPresent()) {
            audioEngine.get().stop(currentPlaybackHandle.get());
            currentPlaybackHandle.get().close();
        }
        currentPlaybackHandle = Optional.empty();

        // Clear handles (audio handle doesn't need explicit closing)
        currentAudioHandle = Optional.empty();

        // Clear state and cached values
        currentFile = Optional.empty();
        errorMessage = null;
        totalFrames = 0;
        sampleRate = 0;
    }
}
