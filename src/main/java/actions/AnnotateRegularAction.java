package actions;

import components.annotations.Annotation;
import components.annotations.AnnotationDisplay;
import components.annotations.AnnotationFileParser;
import components.wordpool.WordpoolDisplay;
import components.wordpool.WordpoolWord;
import control.AudioState;
import control.ErrorRequestedEvent;
import control.FocusRequestedEvent;
import control.UIUpdateRequestedEvent;
import info.Constants;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.EventDispatchBus;
import util.OSPath;

/**
 * Commits a regular user's annotation, updating the annotation file and program window as
 * appropriate.
 */
@Singleton
public class AnnotateRegularAction extends BaseAction {
    private static final Logger logger = LoggerFactory.getLogger(AnnotateRegularAction.class);

    private final AudioState audioState;
    private final DeleteSelectedAnnotationAction deleteSelectedAnnotationAction;
    private final EventDispatchBus eventBus;
    private final WordpoolDisplay wordpoolDisplay;
    private String annotatorName;

    @Inject
    public AnnotateRegularAction(
            AudioState audioState,
            DeleteSelectedAnnotationAction deleteSelectedAnnotationAction,
            EventDispatchBus eventBus,
            WordpoolDisplay wordpoolDisplay) {
        super("Annotate Regular", "Commit a regular annotation");
        this.audioState = audioState;
        this.deleteSelectedAnnotationAction = deleteSelectedAnnotationAction;
        this.eventBus = eventBus;
        this.wordpoolDisplay = wordpoolDisplay;
    }

    @Override
    protected void performAction(ActionEvent e) {
        // do nothing if no audio file is open
        if (!audioState.audioOpen()) {
            wordpoolDisplay.clearText();
            return;
        }

        // retrieve time associated with annotation
        double time = audioState.getMaster().framesToMillis(audioState.getAudioProgress());

        // retrieve text associated with annotation
        String text = wordpoolDisplay.getFieldText();
        if (text.length() == 0) {
            return; // Regular annotations require text
        }

        // find whether the text matches a wordpool entry, so we can find the wordpool number of the
        // annotation text
        WordpoolWord match = wordpoolDisplay.findMatchingWordpooWord(text);
        if (match == null) {
            // words not from the wordpool must be marked as intrusions for regular mode
            return;
        }

        // append the new annotation to the end of the temporary annotation file
        File oFile = getOutputFile();

        if (!oFile.exists()) {
            try {
                oFile.createNewFile();
            } catch (IOException e1) {
                logger.error("Could not create annotation file: " + oFile.getAbsolutePath(), e1);
                eventBus.publish(
                        new ErrorRequestedEvent(
                                "Could not create "
                                        + Constants.temporaryAnnotationFileExtension
                                        + " file."));
                return;
            }
        }

        if (oFile.exists()) {
            try {
                // check for header
                if (!AnnotationFileParser.headerExists(oFile)) {
                    if (annotatorName == null) {
                        annotatorName = "Default"; // Use default name for now
                    }
                    AnnotationFileParser.prependHeader(oFile, annotatorName);
                }

                Annotation ann = new Annotation(time, match.getNum(), match.getText());

                // check if we are annotating the same position as an existing annotation, if so
                // delete
                deleteSelectedAnnotationAction.actionPerformed(
                        new ActionEvent(
                                wordpoolDisplay,
                                ActionEvent.ACTION_PERFORMED,
                                null,
                                System.currentTimeMillis(),
                                0));
                eventBus.publish(
                        new UIUpdateRequestedEvent(
                                UIUpdateRequestedEvent.Component.WAVEFORM_DISPLAY));

                // file may no longer exist after deletion
                if (!oFile.exists()) {
                    if (oFile.createNewFile()) {
                        if (annotatorName == null) {
                            annotatorName = "Default"; // Use default name for now
                        }
                        if (!AnnotationFileParser.headerExists(oFile)) {
                            AnnotationFileParser.prependHeader(oFile, annotatorName);
                        }
                    } else {
                        throw new IOException("Could not re-create file.");
                    }
                }

                // add a new annotation object, and clear the field
                AnnotationFileParser.appendAnnotation(ann, oFile);
                AnnotationDisplay.addAnnotation(ann);
                wordpoolDisplay.clearText();
            } catch (IOException e1) {
                logger.error("Error committing annotation to file: " + oFile.getAbsolutePath(), e1);
                eventBus.publish(
                        new ErrorRequestedEvent(
                                "Error committing annotation! Check files for damage."));
            }
        }

        // return focus to the frame after annotation, for the sake of action key bindings
        eventBus.publish(new FocusRequestedEvent(FocusRequestedEvent.Component.MAIN_WINDOW));
    }

    @Override
    public void update() {
        if (audioState.audioOpen()) {
            setEnabled(audioState.getPlayer().getStatus() != audio.AudioPlayer.Status.PLAYING);
        } else {
            setEnabled(false);
            wordpoolDisplay.clearText();
        }
    }

    public void setAnnotatorName(String annotatorName) {
        this.annotatorName = annotatorName;
    }

    private File getOutputFile() {
        String curFileName = audioState.getCurrentAudioFileAbsolutePath();
        return new File(
                OSPath.basename(curFileName) + "." + Constants.temporaryAnnotationFileExtension);
    }
}
