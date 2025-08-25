package control;

import audio.AudioProgressHandler;
import events.AudioEvent;
import events.AudioStateEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.EventDispatchBus;

/**
 * Coordinates audio playback events with a hybrid approach for optimal performance and thread
 * safety.
 *
 * <p>This coordinator implements a hybrid architecture:
 *
 * <ul>
 *   <li><strong>Fast Progress Updates:</strong> Direct callback to AudioProgressHandler for
 *       high-frequency progress updates (~30fps) that must be lightweight to avoid audio glitches
 *   <li><strong>EDT-Safe State Events:</strong> Events published to EventDispatchBus for proper
 *       Swing thread safety and UI updates
 * </ul>
 *
 * <p>This design separates concerns between performance-critical progress updates and UI state
 * changes that require EDT execution.
 */
@Singleton
public class AudioPlaybackCoordinator implements AudioEvent.Listener {
    private static final Logger logger = LoggerFactory.getLogger(AudioPlaybackCoordinator.class);

    private final AudioProgressHandler progressHandler;
    private final EventDispatchBus eventBus;

    @Inject
    public AudioPlaybackCoordinator(
            AudioProgressHandler progressHandler, EventDispatchBus eventBus) {
        this.progressHandler = progressHandler;
        this.eventBus = eventBus;
    }

    /**
     * Fast progress update - called directly in playback thread.
     *
     * <p>This method is called approximately 30 times per second during audio playback. It
     * delegates directly to the progress handler for maximum performance.
     *
     * @param frame Current playback frame position
     */
    @Override
    public void onProgress(long frame) {
        progressHandler.updateProgress(frame);
    }

    /**
     * Audio state event - published to EventDispatchBus for EDT-safe handling.
     *
     * <p>This method is called in a separate event thread and publishes the event to
     * EventDispatchBus to ensure all UI updates happen on the EDT.
     *
     * @param event The audio event that occurred
     */
    @Override
    public void onEvent(AudioEvent event) {
        logger.debug("Publishing audio state event: {}", event.type());
        eventBus.publish(new AudioStateEvent(event));
    }
}
