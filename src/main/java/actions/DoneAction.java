package actions;

import audio.AudioPlayer;
import components.audiofiles.AudioFile.AudioFilePathException;
import control.AudioState;
import control.ErrorRequestedEvent;
import info.Constants;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.ActionEvent;
import java.io.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.EventBus;
import util.OSPath;

/**
 * Marks the current annotation file complete and then switches program state to reflect that no
 * audio file is open.
 *
 * <p>Afterward sends update to all UpdatingActions.
 */
@Singleton
public class DoneAction extends BaseAction {
    private static final Logger logger = LoggerFactory.getLogger(DoneAction.class);

    private final AudioState audioState;
    private final EventBus eventBus;

    @Inject
    public DoneAction(AudioState audioState, EventBus eventBus) {
        super("Mark Complete", "Mark current annotation file complete");
        this.audioState = audioState;
        this.eventBus = eventBus;
    }

    @Override
    protected void performAction(ActionEvent e) {
        String curFileName = audioState.getCurrentAudioFileAbsolutePath();
        File tmpFile =
                new File(
                        OSPath.basename(curFileName)
                                + "."
                                + Constants.temporaryAnnotationFileExtension);
        if (tmpFile.exists()) {
            File oFile =
                    new File(
                            OSPath.basename(tmpFile.getAbsolutePath())
                                    + "."
                                    + Constants.completedAnnotationFileExtension);
            if (oFile.exists()) {
                // Fire error requested event - UI will handle showing the error dialog
                eventBus.publish(
                        new ErrorRequestedEvent(
                                "Output file already exists. You should not be able to reach this"
                                        + " condition."));
                return;
            } else {
                if (!tmpFile.renameTo(oFile)) {
                    // Fire error requested event - UI will handle showing the error dialog
                    eventBus.publish(new ErrorRequestedEvent("Operation failed."));
                    return;
                } else {
                    try {
                        audioState.getMaster().getAudioFile().updateDoneStatus();
                    } catch (AudioFilePathException e1) {
                        logger.error("Failed to update audio file done status", e1);
                    }
                    audioState.switchFile(null);
                }
            }
        } else {
            // Fire error requested event - UI will handle showing the error dialog
            eventBus.publish(new ErrorRequestedEvent("You have not made any annotations yet."));
            return;
        }
    }

    /** A file can be marked done only if audio is open and not playing. */
    @Override
    public void update() {
        if (audioState.audioOpen()) {
            if (audioState.getPlayer().getStatus() == AudioPlayer.Status.PLAYING == false) {
                setEnabled(true);
            } else {
                setEnabled(false);
            }
        } else {
            setEnabled(false);
        }
    }
}
