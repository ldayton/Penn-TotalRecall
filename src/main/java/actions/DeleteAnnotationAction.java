package actions;

import components.MyMenu;
import components.annotations.Annotation;
import components.annotations.AnnotationDisplay;
import components.annotations.AnnotationFileParser;
import control.AudioState;
import control.ErrorRequestedEvent;
import info.Constants;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import javax.swing.Action;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.EventBus;
import util.OSPath;

/**
 * Deletes an annotation that has already been committed to a temporary annotation file.
 *
 * <p>If the annotations is the last available, also deletes the temporary annotation file, which
 * should at this point be empty.
 */
@Singleton
public class DeleteAnnotationAction extends BaseAction {
    private static final Logger logger = LoggerFactory.getLogger(DeleteAnnotationAction.class);

    private final AudioState audioState;
    private final EventBus eventBus;
    private int rowIndex;
    private Annotation annToDelete;

    /** Creates an Action for dependency injection. The row index will be set later. */
    @Inject
    public DeleteAnnotationAction(AudioState audioState, EventBus eventBus) {
        super("Delete Annotation", "Delete the selected annotation from the file");
        this.audioState = audioState;
        this.eventBus = eventBus;
    }

    /**
     * Sets the row index for the annotation to delete.
     *
     * @param rowIndex The row index of the annotation to delete
     */
    public void setRowIndex(int rowIndex) {
        this.rowIndex = rowIndex;
        this.annToDelete = AnnotationDisplay.getAnnotationsInOrder()[rowIndex];
        this.putValue(Action.NAME, "Delete Annotation");
    }

    /**
     * Performs the action by calling {@link AnnotationFileParser#removeAnnotation(Annotation,
     * File)}.
     *
     * <p>Warns on failure using events.
     *
     * @param e The ActionEvent provided by the trigger
     */
    @Override
    protected void performAction(ActionEvent e) {
        if (annToDelete == null) {
            logger.error("annToDelete is null - setRowIndex must be called before actionPerformed");
            return;
        }

        String curFileName = audioState.getCurrentAudioFileAbsolutePath();
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
                    eventBus.publish(
                            new ErrorRequestedEvent(
                                    "Deletion of annotation successful, but could not remove"
                                            + " temporary annotation file."));
                }
            }
        } else {
            eventBus.publish(
                    new ErrorRequestedEvent(
                            "Deletion not successful. Files may be damaged. Check file system."));
        }

        MyMenu.updateActions();
    }

    /**
     * The user can delete an annotation when audio is open and there is at least one annotation to
     * the current file.
     */
    @Override
    public void update() {
        if (audioState.audioOpen() && AnnotationDisplay.getNumAnnotations() > 0) {
            setEnabled(true);
        } else {
            setEnabled(false);
        }
    }
}
