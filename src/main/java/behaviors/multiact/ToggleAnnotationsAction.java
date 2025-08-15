package behaviors.multiact;

import audio.PrecisionPlayer;
import components.MyFrame;
import components.annotations.Annotation;
import components.annotations.AnnotationDisplay;
import control.CurAudio;
import java.awt.event.ActionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.GiveMessage;

/**
 * Tries to move the audio position to the next/previous {@link components.annotations.Annotation},
 * relative to current audio position.
 *
 * <p>Afterward sends update to all <code>UpdatingActions</code>.
 */
public class ToggleAnnotationsAction extends IdentifiedMultiAction {
    private static final Logger logger = LoggerFactory.getLogger(ToggleAnnotationsAction.class);

    /** Defines the toggling direction of a <code>ToggleAnnotationAction</code> instance. */
    public enum Direction {
        FORWARD,
        BACKWARD
    }

    private final Direction myDir;

    /**
     * Create an action with the direction presets given by the provided <code>Enum</code>.
     *
     * @param dir An <code>Enum</code> defined in this class which maps to the correct direction of
     *     toggling
     * @see behaviors.multiact.IdentifiedMultiAction#IdentifiedMultiAction(Enum)
     */
    public ToggleAnnotationsAction(Direction dir) {
        super(dir);
        this.myDir = dir;
    }

    /**
     * Performs the toggling, moving the audio position to the next/previous annotation.
     *
     * Afterward sends an update to all <code>UpdatingActions<code>.
     *
     * Since the waveform display autonomously decides when to paint itself, this action may not result in an instant visual change.
     *
     * <p>Prints warnings if an appropriate Annotation could not be found, despite the action being enabled.
     *
     * @param e The <code>ActionEvent</code> provided by the trigger
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        super.actionPerformed(e);
        Annotation ann =
                findAnnotation(
                        myDir, CurAudio.getMaster().framesToMillis(CurAudio.getAudioProgress()));
        if (ann == null) {
            logger.error(
                    "It should not have been possible to call "
                            + getClass().getName()
                            + ". Could not find matching annotation");
        } else {
            long approxFrame = CurAudio.getMaster().millisToFrames(ann.getTime());
            if (approxFrame < 0 || approxFrame > CurAudio.getMaster().durationInFrames() - 1) {
                GiveMessage.errorMessage(
                        "The annotation I am toggling to isn't in range.\n"
                                + "Please check annotation file for errors.");
                return;
            }
            CurAudio.setAudioProgressAndUpdateActions(approxFrame);
            CurAudio.getPlayer().queuePlayAt(approxFrame);
        }
        MyFrame.getInstance().requestFocusInWindow();
    }

    /**
     * A forward (backward) <code>ToggleAnnotationsAction</code> should be enabled only when audio
     * is open, not playing, and when there is an annotation following (preceding) the current
     * position.
     */
    @Override
    public void update() {
        if (CurAudio.audioOpen()) {
            if (CurAudio.getPlayer().getStatus() == PrecisionPlayer.Status.PLAYING) {
                setEnabled(false);
            } else {
                double curTimeMillis =
                        CurAudio.getMaster().framesToMillis(CurAudio.getAudioProgress());
                if (findAnnotation(myDir, curTimeMillis) != null) {
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
     * @param dir The direction of movement
     * @param curTimeMillis The present time in milliseconds
     * @return In principle, the <code>Annotation</code> after/before <code>curTimeMillis</code>
     */
    private Annotation findAnnotation(Direction dir, double curTimeMillis) {
        Annotation[] anns = AnnotationDisplay.getAnnotationsInOrder();
        if (myDir == Direction.FORWARD) {
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
