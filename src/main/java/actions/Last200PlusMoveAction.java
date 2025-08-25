package actions;

import events.ErrorRequestedEvent;
import events.EventDispatchBus;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.ActionEvent;
import javax.swing.Action;
import state.AudioState;

/** Moves the audio position by a small amount and then replays the last 200ms. */
@Singleton
public class Last200PlusMoveAction extends BaseAction {
    private final AudioState audioState;
    private final EventDispatchBus eventBus;

    @Inject
    public Last200PlusMoveAction(AudioState audioState, EventDispatchBus eventBus) {
        super("Last200PlusMove", "Move and replay last 200ms");
        this.audioState = audioState;
        this.eventBus = eventBus;
    }

    @Override
    protected void performAction(ActionEvent e) {
        if (!audioState.audioOpen()) {
            eventBus.publish(new ErrorRequestedEvent("No audio file is open."));
            return;
        }

        // Get direction from action name
        String actionName = (String) getValue(Action.NAME);
        boolean forward = actionName.contains("Forward");

        long currentFrame = audioState.getAudioProgress();
        long shift = audioState.getCalculator().millisToFrames(200); // 200ms shift

        long newFrame;
        if (forward) {
            newFrame = currentFrame + shift;
        } else {
            newFrame = currentFrame - shift;
        }

        // Ensure we don't go out of bounds
        if (newFrame < 0) {
            newFrame = 0;
        } else if (newFrame >= audioState.getCalculator().durationInFrames()) {
            newFrame = audioState.getCalculator().durationInFrames() - 1;
        }

        // Set the new position
        audioState.setAudioProgressAndUpdateActions(newFrame);

        // Replay the last 200ms from the new position
        long replayStartFrame = newFrame - audioState.getCalculator().millisToFrames(200);
        if (replayStartFrame < 0) {
            replayStartFrame = 0;
        }

        audioState.setAudioProgressAndUpdateActions(replayStartFrame);
        audioState.getPlayer().playAt(replayStartFrame);
    }

    @Override
    public void update() {
        setEnabled(audioState.audioOpen());
    }
}
