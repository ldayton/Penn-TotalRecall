package actions;

import audio.AudioPlayer;
import components.annotations.Annotation;
import components.annotations.AnnotationDisplay;
import components.annotations.AnnotationTable;
import control.AudioState;
import control.ErrorRequestedEvent;
import control.FocusRequestedEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.ActionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.EventBus;

@Singleton
public class JumpToAnnotationAction extends BaseAction {
    private static final Logger logger = LoggerFactory.getLogger(JumpToAnnotationAction.class);

    private final AudioState audioState;
    private final EventBus eventBus;

    @Inject
    public JumpToAnnotationAction(AudioState audioState, EventBus eventBus) {
        super("Jump to Annotation", "Jump to the selected annotation in the table");
        this.audioState = audioState;
        this.eventBus = eventBus;
    }

    @Override
    protected void performAction(ActionEvent e) {
        Annotation targetAnn = AnnotationTable.popSelectedAnnotation();
        if (targetAnn == null) {
            logger.error("selection is invalid, can't jump to Annotation");
        } else {
            long curFrame = audioState.getMaster().millisToFrames(targetAnn.getTime());
            if (curFrame < 0 || curFrame > audioState.getMaster().durationInFrames() - 1) {
                eventBus.publish(
                        new ErrorRequestedEvent(
                                "The annotation I am jumpting to isn't in range.\n"
                                        + "Please check annotation file for errors."));
                return;
            }
            audioState.setAudioProgressAndUpdateActions(curFrame);
            audioState.getPlayer().playAt(curFrame);
        }
        eventBus.publish(new FocusRequestedEvent(FocusRequestedEvent.Component.MAIN_WINDOW));
    }

    @Override
    public void update() {
        if (audioState.audioOpen()) {
            if (audioState.getPlayer().getStatus() == AudioPlayer.Status.PLAYING) {
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
