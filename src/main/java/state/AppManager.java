package state;

import a2.AudioEngine;
import a2.AudioHandle;
import a2.PlaybackHandle;
import a2.PlaybackListener;
import com.google.inject.Provider;
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
 * Lean application manager: tracks state transitions, delegates to the audio engine, and publishes
 * state change events. Maintains only essential state like current file and handles.
 */
@Singleton
@Slf4j
public class AppManager implements PlaybackListener {

    private final AppStateManager stateManager = new AppStateManager();
    private final Provider<AudioEngine> audioEngineProvider;
    private final EventDispatchBus eventBus;

    private Optional<AudioEngine> audioEngine = Optional.empty();
    private Optional<AudioFile> currentFile = Optional.empty();
    private Optional<AudioHandle> currentAudioHandle = Optional.empty();
    private Optional<PlaybackHandle> currentPlaybackHandle = Optional.empty();

    @Inject
    public AppManager(
            @NonNull Provider<AudioEngine> audioEngineProvider,
            @NonNull EventDispatchBus eventBus) {
        this.audioEngineProvider = audioEngineProvider;
        this.eventBus = eventBus;
        eventBus.subscribe(this);
    }

    // Event subscriptions (UI -> AppManager)

    @Subscribe
    public void onAudioFileLoadRequested(@NonNull AudioFileLoadRequestedEvent event) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Subscribe
    public void onAudioPlayPauseRequested(@NonNull AudioPlayPauseRequestedEvent event) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Subscribe
    public void fileclose(@NonNull AudioFileCloseRequestedEvent event) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Subscribe
    public void onAudioSeekRequested(@NonNull AudioSeekRequestedEvent event) {
        throw new UnsupportedOperationException("Not implemented");
    }

    // PlaybackListener
    @Override
    public void onProgress(
            @NonNull PlaybackHandle playback, long positionFrames, long totalFrames) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void onStateChanged(
            @NonNull PlaybackHandle handle,
            @NonNull a2.PlaybackState newState,
            @NonNull a2.PlaybackState oldState) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void onPlaybackComplete(@NonNull PlaybackHandle playback) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
