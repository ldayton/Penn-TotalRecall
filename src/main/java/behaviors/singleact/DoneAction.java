package behaviors.singleact;

import audio.PrecisionPlayer;
import behaviors.multiact.AnnotateAction;
import components.audiofiles.AudioFile.AudioFilePathException;
import control.CurAudio;
import info.Constants;
import info.SysInfo;
import java.awt.event.ActionEvent;
import java.io.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.GiveMessage;
import util.OSPath;

/**
 * Marks the current annotation file complete and then switches program state to reflect that no
 * audio file is open.
 *
 * <p>Afterward sends update to all <code>UpdatingActions</code>.
 */
public class DoneAction extends IdentifiedSingleAction {
    private static final Logger logger = LoggerFactory.getLogger(DoneAction.class);

    public DoneAction() {}

    @Override
    public void actionPerformed(ActionEvent e) {
        super.actionPerformed(e);
        String curFileName = CurAudio.getCurrentAudioFileAbsolutePath();
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
                GiveMessage.errorMessage(
                        "Output file already exists. You should not be able to reach this"
                                + " condition.");
                return;
            } else {
                AnnotateAction.writeSpans();
                if (!tmpFile.renameTo(oFile)) {
                    GiveMessage.errorMessage("Operation failed.");
                    return;
                } else {
                    try {
                        CurAudio.getMaster().getAudioFile().updateDoneStatus();
                    } catch (AudioFilePathException e1) {
                        logger.error("Failed to update audio file done status", e1);
                    }
                    CurAudio.switchFile(null);
                }
            }
        } else {
            GiveMessage.errorMessage("You have not made any annotations yet.");
            return;
        }
    }

    /** A file can be marked done only if audio is open and not playing. */
    @Override
    public void update() {
        if (CurAudio.audioOpen()) {
            if (SysInfo.sys.forceListen) {
                if (CurAudio.getListener().getGreatestProgress()
                        < CurAudio.getMaster().durationInFrames() - 1) {
                    setEnabled(false);
                    return;
                }
            }
            if (CurAudio.getPlayer().getStatus() == PrecisionPlayer.Status.PLAYING == false) {
                setEnabled(true);
            } else {
                setEnabled(false);
            }
        } else {
            setEnabled(false);
        }
    }
}
