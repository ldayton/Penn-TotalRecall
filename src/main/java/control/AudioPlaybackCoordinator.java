package control;

import a2.PlaybackHandle;
import a2.PlaybackListener;
import a2.PlaybackState;
import events.EventDispatchBus;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Coordinates audio playback events between the a2 audio engine and the application. */
@Singleton
public class AudioPlaybackCoordinator implements PlaybackListener {
    private static final Logger logger = LoggerFactory.getLogger(AudioPlaybackCoordinator.class);

    private final PlaybackListener progressHandler;
    private final EventDispatchBus eventBus;

    @Inject
    public AudioPlaybackCoordinator(PlaybackListener progressHandler, EventDispatchBus eventBus) {
        this.progressHandler = progressHandler;
        this.eventBus = eventBus;
    }

    @Override
    public void onProgress(
            @NonNull PlaybackHandle playback, long positionFrames, long totalFrames) {
        // Forward frame progress to the main progress handler
        progressHandler.onProgress(playback, positionFrames, totalFrames);
    }

    @Override
    public void onStateChanged(
            @NonNull PlaybackHandle playback,
            @NonNull PlaybackState newState,
            @NonNull PlaybackState oldState) {
        logger.debug("Playback state changed: {} -> {}", oldState, newState);
        // TODO: Publish state change events when needed
    }

    @Override
    public void onPlaybackComplete(@NonNull PlaybackHandle playback) {
        logger.debug("Playback completed");
        // TODO: Publish completion event when needed
    }

    @Override
    public void onPlaybackError(PlaybackHandle playback, @NonNull String error) {
        logger.error("Playback error: {}", error);
        // TODO: Publish error event when needed
    }
}
