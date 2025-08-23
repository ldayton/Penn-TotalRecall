package behaviors.singleact;

import components.audiofiles.AudioFile;
import components.audiofiles.AudioFile.AudioFilePathException;
import di.GuiceBootstrap;
import info.Constants;
import java.awt.event.ActionEvent;
import java.io.File;
import javax.swing.Action;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.DialogService;
import util.OSPath;

/** Reopens a file which was already done being annotated. */
public class ContinueAnnotatingAction extends IdentifiedSingleAction {
    private static final Logger logger = LoggerFactory.getLogger(ContinueAnnotatingAction.class);

    private AudioFile myAudioFile;

    /**
     * Creates the <code>Action</code> action for the provided <code>File</code>.
     *
     * @param f The audio file whose corresponding annotation file will be reopened
     */
    public ContinueAnnotatingAction(AudioFile f) {
        if (f == null) {
            throw new IllegalArgumentException("file cannot be null");
        }
        this.putValue(Action.NAME, "Continue Editing");
        myAudioFile = f;
    }

    /**
     * Performs the <code>Action</code> by changing a permanent annotation file into a temporary
     * one.
     *
     * <p>That only involves changing the file extensions.
     *
     * @param e The <code>ActionEvent</code> provided by the trigger
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        super.actionPerformed(e);
        if (myAudioFile.isDone() == false) {
            logger.error(
                    "it should not have been possible to call ContinueAnnotatingAction on an"
                            + " incomplete file");
            return;
        }
        File tmpFile =
                new File(
                        OSPath.basename(myAudioFile.getAbsolutePath())
                                + "."
                                + Constants.temporaryAnnotationFileExtension);
        File doneFile =
                new File(
                        OSPath.basename(myAudioFile.getAbsolutePath())
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
            DialogService dialogService = GuiceBootstrap.getInjectedInstance(DialogService.class);
            if (dialogService != null) {
                dialogService.showError("Could not re-open file for annotation.");
            }
            return;
        }
    }

    /** A <code>ContinueAnnotationAction</code> is always enabled. */
    @Override
    public void update() {}
}
