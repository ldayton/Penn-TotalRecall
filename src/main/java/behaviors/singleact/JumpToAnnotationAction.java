package behaviors.singleact;

import audio.AudioPlayer;
import components.MyFrame;
import components.annotations.Annotation;
import components.annotations.AnnotationDisplay;
import components.annotations.AnnotationTable;
import control.CurAudio;
import di.GuiceBootstrap;
import java.awt.event.ActionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.DialogService;

public class JumpToAnnotationAction extends IdentifiedSingleAction {
    private static final Logger logger = LoggerFactory.getLogger(JumpToAnnotationAction.class);

    public JumpToAnnotationAction() {}

    @Override
    public void actionPerformed(ActionEvent e) {
        super.actionPerformed(e);
        Annotation targetAnn = AnnotationTable.popSelectedAnnotation();
        if (targetAnn == null) {
            logger.error("selection is invalid, can't jump to Annotation");
        } else {
            long curFrame = CurAudio.getMaster().millisToFrames(targetAnn.getTime());
            if (curFrame < 0 || curFrame > CurAudio.getMaster().durationInFrames() - 1) {
                DialogService dialogService =
                        GuiceBootstrap.getInjectedInstance(DialogService.class);
                if (dialogService != null) {
                    dialogService.showError(
                            "The annotation I am jumpting to isn't in range.\n"
                                    + "Please check annotation file for errors.");
                }
                return;
            }
            CurAudio.setAudioProgressAndUpdateActions(curFrame);
            CurAudio.getPlayer().playAt(curFrame);
        }
        MyFrame.getInstance().requestFocusInWindow();
    }

    @Override
    public void update() {
        if (CurAudio.audioOpen()) {
            if (CurAudio.getPlayer().getStatus() == AudioPlayer.Status.PLAYING) {
                setEnabled(false);
            } else {
                if (AnnotationDisplay.getNumAnnotations() > 0) {
                    setEnabled(true);
                } else {
                    setEnabled(false);
                }
            }
        } else {
            setEnabled(false);
        }
    }
}
