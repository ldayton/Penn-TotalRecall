package actions;

import env.Constants;
import events.ErrorRequestedEvent;
import events.EventDispatchBus;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.ActionEvent;
import java.io.File;
import javax.swing.Action;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ui.audiofiles.AudioFile;
import ui.audiofiles.AudioFile.AudioFilePathException;
import util.OsPath;

/** Reopens a file which was already done being annotated. */
@Singleton
public class ContinueAnnotatingAction extends BaseAction {
    private static final Logger logger = LoggerFactory.getLogger(ContinueAnnotatingAction.class);

    private final EventDispatchBus eventBus;
    private AudioFile myAudioFile;

    /** Creates the Action for dependency injection. The audio file will be set later. */
    @Inject
    public ContinueAnnotatingAction(EventDispatchBus eventBus) {
        super("Continue Editing", "Reopen a completed annotation file for further editing");
        this.eventBus = eventBus;
    }

    /**
     * Sets the audio file for this action.
     *
     * @param f The audio file whose corresponding annotation file will be reopened
     */
    public void setAudioFile(AudioFile f) {
        if (f == null) {
            throw new IllegalArgumentException("file cannot be null");
        }
        this.myAudioFile = f;
        this.putValue(Action.NAME, "Continue Editing");
    }

    /**
     * Performs the Action by changing a permanent annotation file into a temporary one.
     *
     * <p>That only involves changing the file extensions.
     *
     * @param e The ActionEvent provided by the trigger
     */
    @Override
    protected void performAction(ActionEvent e) {
        if (myAudioFile == null) {
            logger.error(
                    "myAudioFile is null - setAudioFile must be called before actionPerformed");
            return;
        }

        if (myAudioFile.isDone() == false) {
            logger.error(
                    "it should not have been possible to call ContinueAnnotatingAction on an"
                            + " incomplete file");
            return;
        }
        File tmpFile =
                new File(
                        OsPath.basename(myAudioFile.getAbsolutePath())
                                + "."
                                + Constants.temporaryAnnotationFileExtension);
        File doneFile =
                new File(
                        OsPath.basename(myAudioFile.getAbsolutePath())
                                + "."
                                + Constants.completedAnnotationFileExtension);
        if (tmpFile.exists() == true) {
            logger.error(
                    Constants.temporaryAnnotationFileExtension
                            + " file already exists. This should not happen.");
            return;
        }
        if (doneFile.exists() == false) {
            logger.error("Can't find annotation file, to re-open");
            return;
        }
        if (doneFile.renameTo(tmpFile)) {
            try {
                myAudioFile.updateDoneStatus();
            } catch (AudioFilePathException e1) {
                // should not be possible to enter this condition after above checking, so we're not
                // going to specially handle the exception
                logger.error(
                        "Unexpected error updating audio file status after renaming annotation"
                                + " file",
                        e1);
            }
            return;
        } else {
            eventBus.publish(new ErrorRequestedEvent("Could not re-open file for annotation."));
            return;
        }
    }

    /** A ContinueAnnotationAction is always enabled. */
    @Override
    public void update() {}
}
