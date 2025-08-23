package behaviors.multiact;

import audio.AudioPlayer;
import behaviors.UpdatingAction;
import behaviors.singleact.DeleteSelectedAnnotationAction;
import components.MyMenu;
import components.annotations.Annotation;
import components.annotations.AnnotationDisplay;
import components.annotations.AnnotationFileParser;
import components.waveform.WaveformDisplay;
import components.wordpool.WordpoolDisplay;
import components.wordpool.WordpoolWord;
import control.CurAudio;
import di.GuiceBootstrap;
import info.Constants;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.DialogService;
import util.OSPath;
import util.WindowService;

/** Commits a user's annotation, updating the annotation file and program window as appropriate. */
public class AnnotateAction extends IdentifiedMultiAction {
    private static final Logger logger = LoggerFactory.getLogger(AnnotateAction.class);

    public enum Mode {
        INTRUSION,
        REGULAR
    }

    private final Mode mode;

    /** Create an <code>Action</code> corresponding to an intrusion or a normal annotation. */
    public AnnotateAction(Mode mode) {
        super(mode);
        this.mode = mode;
    }

    private static String obfuscate(String in) {
        byte[] inb = in.getBytes();
        StringBuffer buff = new StringBuffer();
        for (byte b : inb) {
            buff.append(b + " ");
        }
        return buff.toString();
    }

    public static File getOutputFile() {
        String curFileName = CurAudio.getCurrentAudioFileAbsolutePath();
        File oFile =
                new File(
                        OSPath.basename(curFileName)
                                + "."
                                + Constants.temporaryAnnotationFileExtension);
        return oFile;
    }

    /**
     * Performs the <code>AnnotationAction</code> by appending the word in the text field to the
     * temporary annotations file.
     *
     * @param e The <code>ActionEvent</code> provided by the trigger.
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        super.actionPerformed(e);
        // do nothing if no audio file is open
        if (CurAudio.audioOpen() == false) {
            WordpoolDisplay.clearText();
            return;
        }

        // retrieve time associated with annotation
        double time = CurAudio.getMaster().framesToMillis(CurAudio.getAudioProgress());

        // retrieve text associated with annotation, possibly the intrusion string
        String text = WordpoolDisplay.getFieldText();
        if (text.length() == 0) {
            if (mode == Mode.INTRUSION) {
                text = Constants.intrusionSoundString;
            } else {
                return;
            }
        }

        // find whether the text matches a wordpool entry, so we can find the wordpool number of the
        // annotation text
        WordpoolWord match = WordpoolDisplay.findMatchingWordpooWord(text);
        if (match == null) {
            if (mode == Mode.REGULAR) { // words not from the wordpool must be marked as intrusions
                return;
            }
            match = new WordpoolWord(text, -1);
        }

        // append the new annotation to the end of the temporary annotation file
        File oFile = getOutputFile();

        if (oFile.exists() == false) {
            try {
                oFile.createNewFile();
            } catch (IOException e1) {
                logger.error("Could not create annotation file: " + oFile.getAbsolutePath(), e1);
                DialogService dialogService =
                        GuiceBootstrap.getInjectedInstance(DialogService.class);
                if (dialogService != null) {
                    dialogService.showError(
                            "Could not create "
                                    + Constants.temporaryAnnotationFileExtension
                                    + " file.");
                }
            }
        }
        if (oFile.exists()) {
            // check for header
            try {
                if (AnnotationFileParser.headerExists(oFile) == false) {

                    String annotatorName = MyMenu.getAnnotator();
                    if (annotatorName == null) {
                        DialogService dialogService =
                                GuiceBootstrap.getInjectedInstance(DialogService.class);
                        if (dialogService != null) {
                            annotatorName = dialogService.showInput("Please enter your name:");
                            if (annotatorName == null || annotatorName.equals("")) {
                                dialogService.showError("Cannot commit annotation without name.");
                                return;
                            }
                        } else {
                            // Fallback if DI not available
                            return;
                        }
                    }
                    MyMenu.setAnnotator(annotatorName);

                    AnnotationFileParser.prependHeader(oFile, annotatorName);
                }

                Annotation ann = new Annotation(time, match.getNum(), match.getText());

                // check if we are annotating the same position as an existing annotation, if so
                // delete
                new DeleteSelectedAnnotationAction()
                        .actionPerformed(
                                new ActionEvent(
                                        WordpoolDisplay.getInstance(),
                                        ActionEvent.ACTION_PERFORMED,
                                        null,
                                        System.currentTimeMillis(),
                                        0));
                WaveformDisplay.getInstance().repaint();

                // file may no longer exist after deletion
                if (oFile.exists() == false) {
                    if (oFile.createNewFile()) {
                        DialogService dialogService =
                                GuiceBootstrap.getInjectedInstance(DialogService.class);
                        if (dialogService != null) {
                            String annotatorName =
                                    dialogService.showInput("Please enter your name:");
                            if (annotatorName == null || annotatorName.equals("")) {
                                dialogService.showError("Cannot commit annotation without name.");
                                return;
                            }
                            if (AnnotationFileParser.headerExists(oFile) == false) {
                                AnnotationFileParser.prependHeader(oFile, annotatorName);
                            }
                        } else {
                            // Fallback if DI not available
                            throw new IOException("Could not re-create file (DI not available).");
                        }
                    } else {
                        throw new IOException("Could not re-create file.");
                    }
                }

                // add a new annotation object, and clear the field
                AnnotationFileParser.appendAnnotation(ann, oFile);
                AnnotationDisplay.addAnnotation(ann);
                WordpoolDisplay.clearText();
            } catch (IOException e1) {
                logger.error("Error committing annotation to file: " + oFile.getAbsolutePath(), e1);
                DialogService dialogService =
                        GuiceBootstrap.getInjectedInstance(DialogService.class);
                if (dialogService != null) {
                    dialogService.showError("Error committing annotation! Check files for damage.");
                }
            }
        }

        // return focus to the frame after annotation, for the sake of action key bindings
        WindowService windowService = GuiceBootstrap.getInjectedInstance(WindowService.class);
        if (windowService != null) {
            windowService.requestFocus();
        }
        MyMenu.updateActions();
    }

    public static void writeSpans() {
        if (UpdatingAction.getStamps().size() > 0) {
            ArrayList<ArrayList<Long>> spans = new ArrayList<ArrayList<Long>>();

            Long[] stamps = UpdatingAction.getStamps().toArray(new Long[] {});
            Arrays.sort(stamps);

            long start = 0L;
            long end = 0L;
            for (long stamp : stamps) {
                if (stamp - end > Constants.timeout) {
                    if (start > 0 && end > start) {
                        ArrayList<Long> nSpan = new ArrayList<Long>();
                        nSpan.add(start);
                        nSpan.add(end);
                        spans.add(nSpan);
                    }
                    start = stamp;
                    end = stamp;
                } else {
                    end = stamp;
                }
            }
            if (start > 0 && end > start) {
                ArrayList<Long> nSpan = new ArrayList<Long>();
                nSpan.add(start);
                nSpan.add(end);
                spans.add(nSpan);
            }

            UpdatingAction.getStamps().clear();

            for (ArrayList<Long> span : spans) {
                String toWrite = "Span: " + span.get(0) + "-" + span.get(1);
                try {
                    AnnotationFileParser.addField(getOutputFile(), obfuscate(toWrite));
                } catch (IOException e) {
                    logger.error("Error adding span field to annotation file", e);
                }
            }
        }
    }

    /** <code>AnnotateActions</code> are enabled anytime audio is open and not playing. */
    @Override
    public void update() {
        if (CurAudio.audioOpen()) {
            if (CurAudio.getPlayer().getStatus() == AudioPlayer.Status.PLAYING) {
                setEnabled(false);
            } else {
                setEnabled(true);
            }
        } else {
            setEnabled(false);
            WordpoolDisplay.clearText();
        }
    }
}
