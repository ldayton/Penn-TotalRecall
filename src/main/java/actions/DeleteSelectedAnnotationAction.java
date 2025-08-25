package actions;

import audio.AudioPlayer;
import components.annotations.Annotation;
import components.annotations.AnnotationDisplay;
import components.waveform.WaveformDisplay;
import events.EventDispatchBus;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.ActionEvent;
import state.AudioState;

@Singleton
public class DeleteSelectedAnnotationAction extends BaseAction {

    private final AudioState audioState;
    private final DeleteAnnotationAction deleteAnnotationAction;
    private final EventDispatchBus eventBus;

    @Inject
    public DeleteSelectedAnnotationAction(
            AudioState audioState,
            DeleteAnnotationAction deleteAnnotationAction,
            EventDispatchBus eventBus) {
        super(
                "Delete Selected Annotation",
                "Delete the annotation at the current playback position");
        this.audioState = audioState;
        this.deleteAnnotationAction = deleteAnnotationAction;
        this.eventBus = eventBus;
    }

    @Override
    protected void performAction(ActionEvent e) {
        long curFrame = audioState.getAudioProgress();
        int progX = WaveformDisplay.frameToAbsoluteXPixel(curFrame);

        Annotation[] anns = AnnotationDisplay.getAnnotationsInOrder();
        for (int i = 0; i < anns.length; i++) {
            int annX =
                    WaveformDisplay.frameToAbsoluteXPixel(
                            audioState.getCalculator().millisToFrames(anns[i].getTime()));
            if (progX == annX) {
                deleteAnnotationAction.setRowIndex(i);
                deleteAnnotationAction.actionPerformed(
                        new ActionEvent(
                                eventBus,
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
