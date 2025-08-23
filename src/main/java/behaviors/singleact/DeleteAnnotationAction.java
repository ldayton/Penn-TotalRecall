package behaviors.singleact;

import components.MyMenu;
import components.annotations.Annotation;
import components.annotations.AnnotationDisplay;
import components.annotations.AnnotationFileParser;
import control.CurAudio;
import di.GuiceBootstrap;
import info.Constants;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import javax.swing.Action;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.DialogService;
import util.OSPath;

/**
 * Deletes an annotation that has already been committed to a temporary annotation file.
 *
 * <p>If the annotations is the last available, also deletes the temporary annotation file, which
 * should at this point be empty.
 */
public class DeleteAnnotationAction extends IdentifiedSingleAction {
    private static final Logger logger = LoggerFactory.getLogger(DeleteAnnotationAction.class);

    private final int rowIndex;
    private final Annotation annToDelete;

    /**
     * Creates an <code>Action</code> that will delete the annotation matching the provided
     * argument.
     *
     * @param rowIndex
     */
    public DeleteAnnotationAction(int rowIndex) {
        this.rowIndex = rowIndex;
        this.annToDelete = AnnotationDisplay.getAnnotationsInOrder()[rowIndex];
        this.putValue(Action.NAME, "Delete Annotation");
    }

    /**
     * Performs the action by calling {@link AnnotationFileParser#removeAnnotation(Annotation,
     * File)}.
     *
     * <p>Warns on failure using dialogs.
     *
     * @param e The <code>ActionEvent</code> provided by the trigger
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        super.actionPerformed(e);
        String curFileName = CurAudio.getCurrentAudioFileAbsolutePath();
        String desiredPath =
                OSPath.basename(curFileName) + "." + Constants.temporaryAnnotationFileExtension;
        File oFile = new File(desiredPath);

        boolean success = false;
        try {
            success = AnnotationFileParser.removeAnnotation(annToDelete, oFile);
        } catch (IOException ex) {
            logger.error("Error deleting annotation from file", ex);
            success = false;
        }
        if (success) {
            AnnotationDisplay.removeAnnotation(rowIndex);

            // no annotations left after removal, so delete file too
            if (AnnotationDisplay.getNumAnnotations() == 0) {
                if (oFile.delete() == false) {
                    DialogService dialogService =
                            GuiceBootstrap.getInjectedInstance(DialogService.class);
                    if (dialogService == null) {
                        throw new IllegalStateException("DialogService not available via DI");
                    }
                    dialogService.showError(
                            "Deletion of annotation successful, but could not remove temporary"
                                    + " annotation file.");
                }
            }
        } else {
            DialogService dialogService = GuiceBootstrap.getInjectedInstance(DialogService.class);
            if (dialogService == null) {
                throw new IllegalStateException("DialogService not available via DI");
            }
            dialogService.showError(
                    "Deletion not successful. Files may be damaged. Check file system.");
        }

        MyMenu.updateActions();
    }

    /**
     * The user can delete an annotation when audio is open and there is at least one annotation to
     * the current file.
     */
    @Override
    public void update() {
        if (CurAudio.audioOpen() && AnnotationDisplay.getNumAnnotations() > 0) {
            setEnabled(true);
        } else {
            setEnabled(false);
        }
    }
}
