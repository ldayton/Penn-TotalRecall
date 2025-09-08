package actions;

import events.EventDispatchBus;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.ActionEvent;
import state.AudioState;
import ui.annotations.Annotation;
import ui.annotations.AnnotationDisplay;
import ui.waveform.WaveformDisplay;

@Singleton
public class DeleteSelectedAnnotationAction extends BaseAction {

    private final AudioState audioState;
    private final DeleteAnnotationAction deleteAnnotationAction;
    private final EventDispatchBus eventBus;
    private final WaveformDisplay waveformDisplay;

    @Inject
    public DeleteSelectedAnnotationAction(
            AudioState audioState,
            DeleteAnnotationAction deleteAnnotationAction,
            EventDispatchBus eventBus,
            WaveformDisplay waveformDisplay) {
        super(
                "Delete Selected Annotation",
                "Delete the annotation at the current playback position");
        this.audioState = audioState;
        this.deleteAnnotationAction = deleteAnnotationAction;
        this.eventBus = eventBus;
        this.waveformDisplay = waveformDisplay;
    }

    @Override
    protected void performAction(ActionEvent e) {
        long curFrame = audioState.getAudioProgress();
        int progX = waveformDisplay.frameToAbsoluteXPixel(curFrame);

        Annotation[] anns = AnnotationDisplay.getAnnotationsInOrder();
        for (int i = 0; i < anns.length; i++) {
            int annX =
                    waveformDisplay.frameToAbsoluteXPixel(
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
        if (audioState.audioOpen() && !audioState.isPlaying()) {
            setEnabled(true);
        } else {
            setEnabled(false);
        }
    }
}
