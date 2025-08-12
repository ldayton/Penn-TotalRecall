package behaviors.singleact;

import audio.PrecisionPlayer;
import components.MySplitPane;
import components.annotations.Annotation;
import components.annotations.AnnotationDisplay;
import components.waveform.WaveformDisplay;
import control.CurAudio;
import java.awt.event.ActionEvent;

public class DeleteSelectedAnnotationAction extends IdentifiedSingleAction {

    @Override
    public void actionPerformed(ActionEvent e) {
        super.actionPerformed(e);
        long curFrame = CurAudio.getAudioProgress();
        int progX = WaveformDisplay.frameToAbsoluteXPixel(curFrame);

        Annotation[] anns = AnnotationDisplay.getAnnotationsInOrder();
        for (int i = 0; i < anns.length; i++) {
            int annX =
                    WaveformDisplay.frameToAbsoluteXPixel(
                            CurAudio.getMaster().millisToFrames(anns[i].getTime()));
            if (progX == annX) {
                new DeleteAnnotationAction(i)
                        .actionPerformed(
                                new ActionEvent(
                                        MySplitPane.getInstance(),
                                        ActionEvent.ACTION_PERFORMED,
                                        null,
                                        System.currentTimeMillis(),
                                        0));
                return;
            }
        }
    }

    @Override
    public void update() {
        if (CurAudio.audioOpen()
                && CurAudio.getPlayer().getStatus() != PrecisionPlayer.Status.PLAYING) {
            setEnabled(true);
        } else {
            setEnabled(false);
        }
    }
}
