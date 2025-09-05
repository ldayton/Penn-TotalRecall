package s2;

import a2.AudioEngine;
import a2.AudioHandle;
import a2.PlaybackHandle;
import a2.PlaybackListener;
import com.google.inject.Provider;
import events.AppStateChangedEvent;
import events.AudioFileCloseRequestedEvent;
import events.AudioFileLoadRequestedEvent;
import events.AudioPlayPauseRequestedEvent;
import events.AudioSeekRequestedEvent;
import events.EventDispatchBus;
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
public class AudioSessionManager implements PlaybackListener, WaveformSessionSource {

    private final AudioSessionStateMachine stateManager = new AudioSessionStateMachine();
    private final Provider<AudioEngine> audioEngineProvider;
    private final EventDispatchBus eventBus;

    private Optional<AudioEngine> audioEngine = Optional.empty();
    private Optional<AudioFile> currentFile = Optional.empty();
    private Optional<AudioHandle> currentAudioHandle = Optional.empty();
    private Optional<PlaybackHandle> currentPlaybackHandle = Optional.empty();
    private String errorMessage = null;

    @Inject
    public AudioSessionManager(
            @NonNull Provider<AudioEngine> audioEngineProvider,
            @NonNull EventDispatchBus eventBus) {
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
        currentPlaybackHandle.ifPresent(
                playback -> {
                    audioEngine.ifPresent(
                            engine -> {
                                engine.seek(playback, event.getFrame());
                                log.debug("Seeked to frame: {}", event.getFrame());
                            });
                });
    }

    // PlaybackListener
    @Override
    public void onProgress(
            @NonNull PlaybackHandle playback, long positionFrames, long totalFrames) {
        // Progress updates handled here, could publish events if needed
    }

    @Override
    public void onStateChanged(
            @NonNull PlaybackHandle handle,
            @NonNull a2.PlaybackState newState,
            @NonNull a2.PlaybackState oldState) {
        // Handle state changes from audio engine
        log.debug("Playback state changed: {} -> {}", oldState, newState);

        // If audio engine reports an error during playback, transition to error state
        if (newState == a2.PlaybackState.ERROR
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
        eventBus.publish(
                new AppStateChangedEvent(
                        prevState, AudioSessionStateMachine.State.READY, "completed"));
        log.debug("Playback completed");
    }

    // WaveformSessionSource implementation

    @Override
    public Optional<Double> getPlaybackPosition() {
        if (currentPlaybackHandle.isEmpty() || audioEngine.isEmpty()) {
            return Optional.empty();
        }
        var position = audioEngine.get().getPosition(currentPlaybackHandle.get());
        var metadata = audioEngine.get().getMetadata(currentAudioHandle.get());
        return Optional.of((double) position / metadata.sampleRate());
    }

    @Override
    public Optional<Double> getTotalDuration() {
        if (currentAudioHandle.isEmpty() || audioEngine.isEmpty()) {
            return Optional.empty();
        }
        var metadata = audioEngine.get().getMetadata(currentAudioHandle.get());
        return Optional.of(metadata.durationSeconds());
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

    /**
     * Stop playback and transition to READY state. Used when user explicitly stops playback (not
     * pause).
     */
    public void stopPlayback() {
        if (currentPlaybackHandle.isPresent() && audioEngine.isPresent()) {
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

        // Clear state
        currentFile = Optional.empty();
        errorMessage = null;
    }
}
