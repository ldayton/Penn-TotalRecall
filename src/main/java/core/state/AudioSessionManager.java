package core.state;

import com.google.inject.Provider;
import core.audio.AudioEngine;
import core.audio.AudioHandle;
import core.audio.PlaybackHandle;
import core.audio.PlaybackListener;
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
public class AudioSessionManager implements PlaybackListener, WaveformSessionDataSource {

    private final AudioSessionStateMachine stateManager;
    private final Provider<AudioEngine> audioEngineProvider;
    private final EventDispatchBus eventBus;

    private Optional<AudioEngine> audioEngine = Optional.empty();
    private Optional<File> currentFile = Optional.empty();
    private Optional<AudioHandle> currentAudioHandle = Optional.empty();
    private Optional<PlaybackHandle> currentPlaybackHandle = Optional.empty();
    private String errorMessage = null;

    // Progress tracking
    private volatile long currentPositionFrames = 0;
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
        var currentState = stateManager.getCurrentState();
        stateManager.transitionToLoading();
        currentFile = Optional.of(event.getFile());
        eventBus.publish(
                new AppStateChangedEvent(
                        previousState, AudioSessionStateMachine.State.LOADING, event.getFile()));

        // Initialize audio engine if needed
        if (audioEngine.isEmpty()) {
            var engine = audioEngineProvider.get();
            engine.addPlaybackListener(this);
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
            this.currentPositionFrames = 0;

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
                            long startFrame =
                                    Math.max(0, Math.min(currentPositionFrames, totalFrames));
                            long endFrame = totalFrames; // exclusive upper bound
                            var playback = audioEngine.get().play(handle, startFrame, endFrame);
                            currentPlaybackHandle = Optional.of(playback);
                            var prevState = stateManager.getCurrentState();
                            stateManager.transitionToPlaying();
                            var position = audioEngine.get().getPosition(playback);
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
                            var position = audioEngine.get().getPosition(playback);
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
                            var position = audioEngine.get().getPosition(playback);
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
        // Calculate target frame based on current position and requested amount
        long shiftFrames = (long) ((event.milliseconds() / 1000.0) * sampleRate);
        long targetFrame =
                event.direction() == SeekByAmountEvent.Direction.FORWARD
                        ? currentPositionFrames + shiftFrames
                        : currentPositionFrames - shiftFrames;

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
                        currentPositionFrames = event.frame(); // Update cached position
                        log.debug("Seeked to frame: {}", event.frame());
                    });
        } else if (state == AudioSessionStateMachine.State.READY
                && currentAudioHandle.isPresent()) {
            // Avoid creating a playback handle in READY to prevent audible glitches.
            // Cache the target frame; apply when playback starts.
            long clamped = Math.max(0, Math.min(event.frame(), Math.max(0, totalFrames)));
            currentPositionFrames = clamped;
            log.debug("Cached seek target in READY: frame {}", clamped);
        }
    }

    @Subscribe
    public void onAudioStopRequested(@NonNull SeekToStartEvent event) {
        var state = stateManager.getCurrentState();

        if (state == AudioSessionStateMachine.State.PLAYING
                || state == AudioSessionStateMachine.State.PAUSED) {
            // Stop playback and reset position to beginning
            stopPlayback();
            log.debug("Stopped playback and reset to beginning");
        } else if (state == AudioSessionStateMachine.State.READY) {
            // Already stopped, just reset position
            currentPositionFrames = 0;
            log.debug("Reset position to beginning");
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

            // Play the interval from 200ms ago to current position
            audioEngine.get().play(currentAudioHandle.get(), startFrame, endFrame);

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

    // PlaybackListener
    @Override
    public void onProgress(
            @NonNull PlaybackHandle playback, long positionFrames, long totalFrames) {
        // Store progress for efficient access
        this.currentPositionFrames = positionFrames;
        this.totalFrames = totalFrames;
    }

    @Override
    public void onStateChanged(
            @NonNull PlaybackHandle handle,
            @NonNull core.audio.PlaybackState newState,
            @NonNull core.audio.PlaybackState oldState) {
        // Handle state changes from audio engine
        log.debug("Playback state changed: {} -> {}", oldState, newState);

        // If audio engine reports an error during playback, transition to error state
        if (newState == core.audio.PlaybackState.ERROR
                && stateManager.getCurrentState() == AudioSessionStateMachine.State.PLAYING) {
            var prevState = stateManager.getCurrentState();
            stateManager.transitionToError();
            errorMessage = "Playback error occurred";
            eventBus.publish(
                    new AppStateChangedEvent(
                            prevState, AudioSessionStateMachine.State.ERROR, "Playback error"));
        }
    }

    @Override
    public void onPlaybackComplete(@NonNull PlaybackHandle playback) {
        // Playback finished, transition to ready state
        var prevState = stateManager.getCurrentState();
        stateManager.transitionToReady();
        currentPlaybackHandle = Optional.empty();
        currentPositionFrames = 0; // Reset position
        eventBus.publish(
                new AppStateChangedEvent(
                        prevState, AudioSessionStateMachine.State.READY, "completed"));
        log.debug("Playback completed");
    }

    // WaveformSessionSource implementation

    @Override
    public Optional<Double> getPlaybackPosition() {
        if (currentAudioHandle.isEmpty() || sampleRate == 0) {
            return Optional.empty();
        }
        // Use cached position for efficiency
        return Optional.of((double) currentPositionFrames / sampleRate);
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
        // Return the exact frame position without conversion
        return Optional.of(currentPositionFrames);
    }

    @Override
    public Optional<Long> getTotalFrames() {
        if (currentAudioHandle.isEmpty() || totalFrames == 0) {
            return Optional.empty();
        }
        return Optional.of(totalFrames);
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
            currentPositionFrames = 0; // Reset position on stop

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
        currentPositionFrames = 0;
        totalFrames = 0;
        sampleRate = 0;
    }
}
