package core.audio.session;

import com.google.errorprone.annotations.ThreadSafe;
import com.google.inject.Provider;
import core.audio.AudioEngine;
import core.audio.AudioHandle;
import core.audio.exceptions.AudioLoadException;
import core.dispatch.EventDispatchBus;
import core.dispatch.Subscribe;
import core.events.AudioFileLoadRequestedEvent;
import core.events.CloseAudioFileEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.File;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages the lifecycle of audio sessions. Responsible for creating, maintaining, and disposing of
 * AudioSession instances.
 */
@ThreadSafe
@Singleton
@Slf4j
public class AudioSessionManager implements AudioSessionDataSource {

    private final EventDispatchBus eventBus;
    private final Provider<AudioEngine> audioEngineProvider;
    private final AudioSessionStateMachine stateMachine;

    // Current session - only one session can be active at a time
    private Optional<AudioSession> currentSession = Optional.empty();
    private Optional<AudioEngine> audioEngine = Optional.empty();
    private Optional<String> lastErrorMessage = Optional.empty();

    @Inject
    public AudioSessionManager(
            @NonNull EventDispatchBus eventBus,
            @NonNull Provider<AudioEngine> audioEngineProvider,
            @NonNull AudioSessionStateMachine stateMachine) {

        this.eventBus = eventBus;
        this.audioEngineProvider = audioEngineProvider;
        this.stateMachine = stateMachine;

        eventBus.subscribe(this);
        log.debug("AudioSessionManager initialized");
    }

    /** Load an audio file and create a new session. Closes any existing session first. */
    @Subscribe
    public void onLoadRequested(@NonNull AudioFileLoadRequestedEvent event) {
        log.info("Loading audio file: {}", event.getFile().getName());

        // Close existing session if present
        closeCurrentSession();

        // Load asynchronously
        CompletableFuture.runAsync(() -> loadFile(event.getFile()));
    }

    /** Close the current audio session. */
    @Subscribe
    public void onCloseRequested(@NonNull CloseAudioFileEvent event) {
        log.info("Closing current audio session");
        closeCurrentSession();
    }

    /** Load an audio file and create a session. */
    private void loadFile(File file) {
        try {
            // Transition state machine
            stateMachine.transitionToLoading();

            // Get or create audio engine
            if (audioEngine.isEmpty()) {
                audioEngine = Optional.of(audioEngineProvider.get());
                log.debug("Created new audio engine");
            }

            // Load the audio file
            AudioHandle handle = audioEngine.get().loadAudio(file.getAbsolutePath());
            var metadata = audioEngine.get().getMetadata(handle);

            // Create new session
            AudioSession session =
                    new AudioSession(
                            eventBus,
                            audioEngine.get(),
                            stateMachine,
                            file,
                            handle,
                            metadata.frameCount(),
                            metadata.sampleRate());

            currentSession = Optional.of(session);
            lastErrorMessage = Optional.empty(); // Clear any previous error
            stateMachine.transitionToReady();

            log.info(
                    "Successfully loaded: {} ({} frames at {} Hz)",
                    file.getName(),
                    metadata.frameCount(),
                    metadata.sampleRate());

        } catch (AudioLoadException e) {
            log.error("Failed to load audio file: {}", file.getName(), e);
            lastErrorMessage = Optional.of(e.getMessage());
            stateMachine.transitionToError();
            closeCurrentSession();
        } catch (Exception e) {
            log.error("Unexpected error loading audio file: {}", file.getName(), e);
            lastErrorMessage = Optional.of("Unexpected error: " + e.getMessage());
            stateMachine.transitionToError();
            closeCurrentSession();
        }
    }

    /** Close and dispose of the current session. */
    private void closeCurrentSession() {
        currentSession.ifPresent(
                session -> {
                    session.dispose();
                    log.debug("Disposed audio session");
                });

        currentSession = Optional.empty();

        // Only transition to NO_AUDIO if we're not already in an error state
        if (stateMachine.getCurrentState() != AudioSessionStateMachine.State.ERROR) {
            stateMachine.transitionToNoAudio();
        }
    }

    /** Shutdown the manager and release all resources. */
    public void shutdown() {
        log.info("Shutting down AudioSessionManager");

        // Close current session
        closeCurrentSession();

        // Close audio engine
        audioEngine.ifPresent(
                engine -> {
                    try {
                        engine.close();
                        log.debug("Closed audio engine");
                    } catch (Exception e) {
                        log.error("Error closing audio engine", e);
                    }
                });

        audioEngine = Optional.empty();

        // Unsubscribe from events
        eventBus.unsubscribe(this);
    }

    // AudioSessionDataSource implementation

    @Override
    public boolean isAudioLoaded() {
        return currentSession.isPresent();
    }

    @Override
    public Optional<Long> getTotalFrames() {
        return currentSession.map(session -> session.getContext().totalFrames());
    }

    @Override
    public Optional<Integer> getSampleRate() {
        return currentSession.map(session -> session.getContext().sampleRate());
    }

    public Optional<File> getCurrentFile() {
        return currentSession.map(AudioSession::getAudioFile);
    }

    @Override
    public boolean isLoading() {
        return stateMachine.getCurrentState() == AudioSessionStateMachine.State.LOADING;
    }

    @Override
    public boolean isPlaying() {
        return stateMachine.getCurrentState() == AudioSessionStateMachine.State.PLAYING;
    }

    @Override
    public Optional<Double> getTotalDuration() {
        return currentSession.map(
                session -> {
                    var ctx = session.getContext();
                    return ctx.sampleRate() > 0
                            ? (double) ctx.totalFrames() / ctx.sampleRate()
                            : 0.0;
                });
    }

    @Override
    public Optional<Double> getPlaybackPosition() {
        return currentSession.map(AudioSession::getCurrentPositionSeconds);
    }

    @Override
    public Optional<Long> getPlaybackPositionFrames() {
        return currentSession.map(AudioSession::getCurrentPositionFrames);
    }

    @Override
    public Optional<AudioHandle> getCurrentAudioHandle() {
        return currentSession.flatMap(session -> session.getContext().audioHandle());
    }

    @Override
    public Optional<String> getCurrentAudioFilePath() {
        return currentSession.map(session -> session.getAudioFile().getAbsolutePath());
    }

    @Override
    public Optional<String> getErrorMessage() {
        return lastErrorMessage;
    }

    // Getters

    /** Get the current session if one exists. */
    public Optional<AudioSession> getCurrentSession() {
        return currentSession;
    }

    /** Get the current state from the state machine. */
    public AudioSessionStateMachine.State getCurrentState() {
        return stateMachine.getCurrentState();
    }
}
