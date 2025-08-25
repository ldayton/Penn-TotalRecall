package control;

import components.AppMenuBar;
import di.GuiceBootstrap;
import events.AudioEvent;
import events.AudioStateEvent;
import events.LayoutUpdateRequestedEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.DialogService;
import util.EventDispatchBus;
import util.Subscribe;

/**
 * Handles audio state events on the Event Dispatch Thread (EDT).
 *
 * <p>This class processes AudioStateEvent instances that are published to EventDispatchBus,
 * ensuring all UI updates happen on the EDT for Swing thread safety.
 *
 * <p>This replaces the event handling logic that was previously in MyPrecisionListener.
 */
@Singleton
public class AudioStateEventHandler {
    private static final Logger logger = LoggerFactory.getLogger(AudioStateEventHandler.class);

    private long greatestProgress = -1;
    private final AudioState audioState;
    private final EventDispatchBus eventBus;

    @Inject
    public AudioStateEventHandler(AudioState audioState, EventDispatchBus eventBus) {
        this.audioState = audioState;
        this.eventBus = eventBus;
        eventBus.subscribe(this);
    }

    /**
     * Handles audio state events on the EDT.
     *
     * @param event The audio state event to process
     */
    @Subscribe
    public void handleAudioStateEvent(AudioStateEvent event) {
        AudioEvent audioEvent = event.getAudioEvent();
        AudioEvent.Type code = audioEvent.type();

        switch (code) {
            case OPENED:
                // Disable continuous layout updates when file is opened
                eventBus.publish(
                        new LayoutUpdateRequestedEvent(
                                LayoutUpdateRequestedEvent.Type.DISABLE_CONTINUOUS));
                break;

            case PLAYING:
                AppMenuBar.updateActions();
                break;

            case STOPPED:
                // This may be a "pause" or a StopAction, no way to tell here
                // handle stops in StopAction
                long currentProgress = audioState.getFramePosition();
                if (currentProgress > audioEvent.frame()) {
                    logger.warn(
                            "current progress {} comes after the current pause/stop {}. isn't that"
                                    + " odd?",
                            currentProgress,
                            audioEvent.frame());
                }
                AppMenuBar.updateActions();
                break;

            case EOM:
                offerGreatestProgress(audioEvent.frame());
                audioState.setAudioProgressAndUpdateActions(audioEvent.frame());
                if (audioState.getAudioProgress()
                        != audioState.getMaster().durationInFrames() - 1) {
                    logger.warn(
                            "the frame reported by EOM event is not the final frame, violating"
                                    + " AudioPlayer spec");
                }
                break;

            case ERROR:
                audioState.setAudioProgressAndUpdateActions(0);
                String error =
                        "An error occurred during audio playback.\n" + audioEvent.errorMessage();
                DialogService dialogService =
                        GuiceBootstrap.getInjectedInstance(DialogService.class);
                if (dialogService == null) {
                    throw new IllegalStateException("DialogService not available via DI");
                }
                dialogService.showError(error);
                break;

            default:
                logger.error("unhandled AudioEvent: " + audioEvent.type());
                break;
        }
    }

    /**
     * Updates the greatest progress tracking.
     *
     * @param frame The frame to consider for greatest progress
     */
    public void offerGreatestProgress(long frame) {
        if (frame > greatestProgress) {
            greatestProgress = frame;
        }
    }

    /**
     * Gets the greatest progress tracked so far.
     *
     * @return The greatest progress frame
     */
    public long getGreatestProgress() {
        return greatestProgress;
    }
}
