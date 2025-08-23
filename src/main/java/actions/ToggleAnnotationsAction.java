package actions;

import audio.AudioPlayer;
import components.annotations.Annotation;
import components.annotations.AnnotationDisplay;
import control.AudioState;
import control.ErrorRequestedEvent;
import control.FocusRequestedEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.ActionEvent;
import javax.swing.Action;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.EventBus;

/**
 * Tries to move the audio position to the next/previous {@link components.annotations.Annotation},
 * relative to current audio position.
 */
@Singleton
public class ToggleAnnotationsAction extends BaseAction {
    private static final Logger logger = LoggerFactory.getLogger(ToggleAnnotationsAction.class);

    private final AudioState audioState;
    private final EventBus eventBus;

    @Inject
    public ToggleAnnotationsAction(AudioState audioState, EventBus eventBus) {
        super("Toggle Annotations", "Move to next/previous annotation");
        this.audioState = audioState;
        this.eventBus = eventBus;
    }

    /** Performs the toggling, moving the audio position to the next/previous annotation. */
    @Override
    protected void performAction(ActionEvent e) {
        // Get direction from action name
        String actionName = (String) getValue(Action.NAME);
        boolean forward = actionName.contains("Next");

        Annotation ann =
                findAnnotation(
                        forward,
                        audioState.getMaster().framesToMillis(audioState.getAudioProgress()));
        if (ann == null) {
            logger.error(
                    "It should not have been possible to call "
                            + getClass().getName()
                            + ". Could not find matching annotation");
        } else {
            long approxFrame = audioState.getMaster().millisToFrames(ann.getTime());
            if (approxFrame < 0 || approxFrame > audioState.getMaster().durationInFrames() - 1) {
                eventBus.publish(
                        new ErrorRequestedEvent(
                                "The annotation I am toggling to isn't in range.\n"
                                        + "Please check annotation file for errors."));
                return;
            }
            audioState.setAudioProgressAndUpdateActions(approxFrame);
            audioState.getPlayer().playAt(approxFrame);
        }
        eventBus.publish(new FocusRequestedEvent());
    }

    /**
     * A forward (backward) <code>ToggleAnnotationsAction</code> should be enabled only when audio
     * is open, not playing, and when there is an annotation following (preceding) the current
     * position.
     */
    @Override
    public void update() {
        if (audioState.audioOpen()) {
            if (audioState.getPlayer().getStatus() == AudioPlayer.Status.PLAYING) {
                setEnabled(false);
            } else {
                // Get direction from action name
                String actionName = (String) getValue(Action.NAME);
                boolean forward = actionName.contains("Next");

                double curTimeMillis =
                        audioState.getMaster().framesToMillis(audioState.getAudioProgress());
                if (findAnnotation(forward, curTimeMillis) != null) {
                    setEnabled(true);
                } else {
                    setEnabled(false);
                }
            }
        } else {
            setEnabled(false);
        }
    }

    /**
     * Finds the next/previous <code>Annotation</code> relative to a certain audio position in
     * milliseconds.
     *
     * @param forward The direction of movement
     * @param curTimeMillis The present time in milliseconds
     * @return In principle, the <code>Annotation</code> after/before <code>curTimeMillis</code>
     */
    private Annotation findAnnotation(boolean forward, double curTimeMillis) {
        Annotation[] anns = AnnotationDisplay.getAnnotationsInOrder();
        if (forward) {
            for (int i = 0; i < anns.length; i++) {
                if (anns[i].getTime() - curTimeMillis > 1) {
                    return anns[i];
                }
            }
        } else {
            for (int i = anns.length - 1; i >= 0; i--) {
                if (curTimeMillis - anns[i].getTime() > 1) {
                    return anns[i];
                }
            }
        }
        return null;
    }
}
