package state;

import audio.AudioEngine;
import audio.AudioHandle;
import audio.PlaybackHandle;
import audio.PlaybackListener;
import com.google.inject.Provider;
import events.AppStateChangedEvent;
import events.AudioFileCloseRequestedEvent;
import events.AudioFileLoadRequestedEvent;
import events.AudioPlayPauseRequestedEvent;
import events.AudioSeekRequestedEvent;
import events.AudioStopRequestedEvent;
import events.EventDispatchBus;
import events.Last200PlusMoveRequestedEvent;
import events.ReplayLast200MillisRequestedEvent;
import events.Subscribe;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Optional;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import ui.audiofiles.AudioFile;

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
    private Optional<AudioFile> currentFile = Optional.empty();
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

        // Close current file if any
        closeCurrentSession();

        // Transition to loading state
        var previousState = stateManager.getCurrentState();
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
    public void onAudioPlayPauseRequested(@NonNull AudioPlayPauseRequestedEvent event) {
        var state = stateManager.getCurrentState();

        switch (state) {
            case READY -> {
                // Start playback from beginning
                currentAudioHandle.ifPresent(
                        handle -> {
                            var playback = audioEngine.get().play(handle);
                            currentPlaybackHandle = Optional.of(playback);
                            var prevState = stateManager.getCurrentState();
                            stateManager.transitionToPlaying();
                            var position = audioEngine.get().getPosition(playback);
                            eventBus.publish(
                                    new AppStateChangedEvent(
                                            prevState,
                                            AudioSessionStateMachine.State.PLAYING,
                                            position));
                            log.debug("Started playback");
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
    public void fileclose(@NonNull AudioFileCloseRequestedEvent event) {
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
    public void onAudioSeekRequested(@NonNull AudioSeekRequestedEvent event) {
        var state = stateManager.getCurrentState();

        if (currentPlaybackHandle.isPresent()) {
            // If we have a playback handle (playing or paused), just seek it
            audioEngine.ifPresent(
                    engine -> {
                        engine.seek(currentPlaybackHandle.get(), event.getFrame());
                        currentPositionFrames = event.getFrame(); // Update cached position
                        log.debug("Seeked to frame: {}", event.getFrame());
                    });
        } else if (state == AudioSessionStateMachine.State.READY
                && currentAudioHandle.isPresent()) {
            // TODO: Fix audio glitch when seeking in READY state
            // Problem: We need a playback handle to seek, but creating one via play()
            // causes a brief moment of audio playback before pause() takes effect.
            // This creates an audible glitch when seeking from READY state.
            // Possible solutions:
            // 1. Track desired position without playback handle, apply on play
            // 2. Add engine support for creating paused playback handles
            // 3. Use play(startFrame, startFrame+1) with immediate stop

            // If in READY state with no playback handle, create one and immediately pause it
            var playback = audioEngine.get().play(currentAudioHandle.get());
            currentPlaybackHandle = Optional.of(playback);
            audioEngine.get().pause(playback);
            audioEngine.get().seek(playback, event.getFrame());
            currentPositionFrames = event.getFrame();

            // Transition to PAUSED since we now have a paused playback handle
            var prevState = stateManager.getCurrentState();
            stateManager.transitionToPaused();
            eventBus.publish(
                    new AppStateChangedEvent(
                            prevState, AudioSessionStateMachine.State.PAUSED, event.getFrame()));

            log.debug("Created paused playback and seeked to frame: {}", event.getFrame());
        }
    }

    @Subscribe
    public void onAudioStopRequested(@NonNull AudioStopRequestedEvent event) {
        var state = stateManager.getCurrentState();

        if (state == AudioSessionStateMachine.State.PLAYING) {
            // Stop playback and reset position to beginning
            stopPlayback();
            log.debug("Stopped playback and reset to beginning");
        }
    }

    @Subscribe
    public void onReplayLast200MillisRequested(@NonNull ReplayLast200MillisRequestedEvent event) {
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
    public void onLast200PlusMoveRequested(@NonNull Last200PlusMoveRequestedEvent event) {
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
                    event.isForward() ? currentFrame + shiftFrames : currentFrame - shiftFrames;

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
                    event.isForward() ? "forward" : "backward",
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
            @NonNull audio.PlaybackState newState,
            @NonNull audio.PlaybackState oldState) {
        // Handle state changes from audio engine
        log.debug("Playback state changed: {} -> {}", oldState, newState);

        // If audio engine reports an error during playback, transition to error state
        if (newState == audio.PlaybackState.ERROR
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
        if (currentPlaybackHandle.isEmpty() || sampleRate == 0) {
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
        if (currentPlaybackHandle.isEmpty()) {
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
