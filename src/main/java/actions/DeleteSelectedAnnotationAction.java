package actions;

import audio.AudioPlayer;
import components.MySplitPane;
import components.annotations.Annotation;
import components.annotations.AnnotationDisplay;
import components.waveform.WaveformDisplay;
import control.AudioState;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.ActionEvent;

@Singleton
public class DeleteSelectedAnnotationAction extends BaseAction {

    private final AudioState audioState;
    private final DeleteAnnotationAction deleteAnnotationAction;

    @Inject
    public DeleteSelectedAnnotationAction(
            AudioState audioState, DeleteAnnotationAction deleteAnnotationAction) {
        super(
                "Delete Selected Annotation",
                "Delete the annotation at the current playback position");
        this.audioState = audioState;
        this.deleteAnnotationAction = deleteAnnotationAction;
    }

    @Override
    protected void performAction(ActionEvent e) {
        long curFrame = audioState.getAudioProgress();
        int progX = WaveformDisplay.frameToAbsoluteXPixel(curFrame);

        Annotation[] anns = AnnotationDisplay.getAnnotationsInOrder();
        for (int i = 0; i < anns.length; i++) {
            int annX =
                    WaveformDisplay.frameToAbsoluteXPixel(
                            audioState.getMaster().millisToFrames(anns[i].getTime()));
            if (progX == annX) {
                deleteAnnotationAction.setRowIndex(i);
                deleteAnnotationAction.actionPerformed(
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
        if (audioState.audioOpen()
                && audioState.getPlayer().getStatus() != AudioPlayer.Status.PLAYING) {
            setEnabled(true);
        } else {
            setEnabled(false);
        }
    }
}
