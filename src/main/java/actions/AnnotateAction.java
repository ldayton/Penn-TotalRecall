package actions;

import audio.AudioPlayer;
import components.annotations.Annotation;
import components.annotations.AnnotationFileParser;
import components.wordpool.WordpoolDisplay;
import components.wordpool.WordpoolWord;
import env.Constants;
import events.AnnotatorNameProvidedEvent;
import events.AnnotatorNameRequestedEvent;
import events.ErrorRequestedEvent;
import events.EventDispatchBus;
import events.FocusRequestedEvent;
import events.Subscribe;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import javax.swing.Action;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import state.AudioState;
import util.OsPath;

/** Commits an annotation with the specified mode. */
@Singleton
public class AnnotateAction extends BaseAction {
    private static final Logger logger = LoggerFactory.getLogger(AnnotateAction.class);

    private final AudioState audioState;
    private final EventDispatchBus eventBus;
    private final WordpoolDisplay wordpoolDisplay;
    private String annotatorName;
    private String pendingAnnotationText;
    private File pendingAnnotationFile;

    @Inject
    public AnnotateAction(
            AudioState audioState, EventDispatchBus eventBus, WordpoolDisplay wordpoolDisplay) {
        super("Annotate", "Commit annotation");
        this.audioState = audioState;
        this.eventBus = eventBus;
        this.wordpoolDisplay = wordpoolDisplay;
        eventBus.subscribe(this);
    }

    @Override
    protected void performAction(ActionEvent e) {
        // do nothing if no audio file is open
        if (!audioState.audioOpen()) {
            wordpoolDisplay.clearText();
            return;
        }

        // retrieve time associated with annotation
        double time = audioState.getCalculator().framesToMillis(audioState.getAudioProgress());

        // Get mode from action name
        String actionName = (String) getValue(Action.NAME);

        // retrieve text associated with annotation
        String text = wordpoolDisplay.getFieldText();
        if (text.length() == 0) {
            if (actionName.contains("Intrusion")) {
                text = Constants.intrusionSoundString;
            } else {
                eventBus.publish(new ErrorRequestedEvent("No text entered for annotation."));
                return;
            }
        }

        // find whether the text matches a wordpool entry
        WordpoolWord match = wordpoolDisplay.findMatchingWordpooWord(text);
        if (match == null) {
            match = new WordpoolWord(text, -1);
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
                        // Store pending annotation data and request name
                        pendingAnnotationText = text;
                        pendingAnnotationFile = oFile;
                        eventBus.publish(new AnnotatorNameRequestedEvent("AnnotateAction"));
                        return;
                    }
                    AnnotationFileParser.prependHeader(oFile, annotatorName);
                }

                // Create annotation based on mode
                Annotation annotation;
                if (actionName.contains("Intrusion")) {
                    annotation = new Annotation(time, match.getNum(), match.getText());
                } else {
                    annotation = new Annotation(time, match.getNum(), match.getText());
                }

                // Write the annotation
                AnnotationFileParser.appendAnnotation(annotation, oFile);

                // Clear the text field
                wordpoolDisplay.clearText();

                // Update the display
                eventBus.publish(
                        new FocusRequestedEvent(FocusRequestedEvent.Component.MAIN_WINDOW));

            } catch (IOException e1) {
                logger.error("Could not write to annotation file: " + oFile.getAbsolutePath(), e1);
                eventBus.publish(
                        new ErrorRequestedEvent(
                                "Could not write to "
                                        + Constants.temporaryAnnotationFileExtension
                                        + " file."));
            }
        }
    }

    private File getOutputFile() {
        String curFileName = audioState.getCurrentAudioFileAbsolutePath();
        return new File(
                OsPath.basename(curFileName) + "." + Constants.temporaryAnnotationFileExtension);
    }

    @Override
    public void update() {
        if (!audioState.audioOpen()) {
            setEnabled(false);
            return;
        }

        // Only enable if not playing
        if (audioState.getPlayer().getStatus() == AudioPlayer.Status.PLAYING) {
            setEnabled(false);
        } else {
            setEnabled(true);
        }
    }

    @Subscribe
    public void handleAnnotatorNameProvided(AnnotatorNameProvidedEvent event) {
        if ("AnnotateAction".equals(event.getCallbackActionId()) && pendingAnnotationText != null) {
            annotatorName = event.getAnnotatorName();

            try {
                AnnotationFileParser.prependHeader(pendingAnnotationFile, annotatorName);

                // Create annotation
                double time =
                        audioState.getCalculator().framesToMillis(audioState.getAudioProgress());
                WordpoolWord match = wordpoolDisplay.findMatchingWordpooWord(pendingAnnotationText);
                if (match == null) {
                    match = new WordpoolWord(pendingAnnotationText, -1);
                }

                Annotation annotation = new Annotation(time, match.getNum(), match.getText());
                AnnotationFileParser.appendAnnotation(annotation, pendingAnnotationFile);

                // Clear the text field
                wordpoolDisplay.clearText();

                // Update the display
                eventBus.publish(
                        new FocusRequestedEvent(FocusRequestedEvent.Component.MAIN_WINDOW));

                // Clear pending data
                pendingAnnotationText = null;
                pendingAnnotationFile = null;

            } catch (IOException e) {
                logger.error(
                        "Could not write to annotation file: "
                                + pendingAnnotationFile.getAbsolutePath(),
                        e);
                eventBus.publish(new ErrorRequestedEvent("Could not write to annotation file."));
            }
        }
    }
}
