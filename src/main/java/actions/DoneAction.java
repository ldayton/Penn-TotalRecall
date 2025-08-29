package actions;

import audio.AudioPlayer;
import env.Constants;
import events.ErrorRequestedEvent;
import events.EventDispatchBus;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.ActionEvent;
import java.io.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import state.AudioState;
import ui.audiofiles.AudioFile.AudioFilePathException;
import util.OsPath;

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
    private final EventDispatchBus eventBus;

    @Inject
    public DoneAction(AudioState audioState, EventDispatchBus eventBus) {
        super("Mark Complete", "Mark current annotation file complete");
        this.audioState = audioState;
        this.eventBus = eventBus;
    }

    @Override
    protected void performAction(ActionEvent e) {
        String curFileName = audioState.getCurrentAudioFileAbsolutePath();
        File tmpFile =
                new File(
                        OsPath.basename(curFileName)
                                + "."
                                + Constants.temporaryAnnotationFileExtension);
        if (tmpFile.exists()) {
            File oFile =
                    new File(
                            OsPath.basename(tmpFile.getAbsolutePath())
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
                        audioState.getCalculator().getAudioFile().updateDoneStatus();
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
