package actions;

import audio.AudioPlayer;
import control.AudioState;
import env.PreferencesManager;
import events.FocusRequestedEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.ActionEvent;
import javax.swing.Action;
import util.EventDispatchBus;
import util.PreferenceKeys;

/**
 * Sets the audio position forward/backward by a pre-defined amount, in response to user request.
 */
@Singleton
public class SeekAction extends BaseAction {

    private final AudioState audioState;
    private final EventDispatchBus eventBus;
    private final PreferencesManager preferencesManager;

    @Inject
    public SeekAction(
            AudioState audioState,
            EventDispatchBus eventBus,
            PreferencesManager preferencesManager) {
        super("Seek", "Seek audio position");
        this.audioState = audioState;
        this.eventBus = eventBus;
        this.preferencesManager = preferencesManager;
    }

    /**
     * Performs the <code>SeekAction</code>, intelligently boundaries to make sure the player isn't
     * taken outside of the audio data.
     */
    @Override
    protected void performAction(ActionEvent e) {
        // Get the action configuration to determine direction and amount
        String actionId = getActionId();
        int shift = getShiftAmount(actionId);
        boolean forward = isForwardDirection(actionId);

        long curFrame = audioState.getAudioProgress();
        long frameShift = audioState.getMaster().millisToFrames(shift);
        long naivePosition = forward ? curFrame + frameShift : curFrame - frameShift;
        long frameLength = audioState.getMaster().durationInFrames();

        long finalPosition = naivePosition;

        if (naivePosition < 0) {
            finalPosition = 0;
        } else if (naivePosition >= frameLength) {
            finalPosition = frameLength - 1;
        }

        audioState.setAudioProgressAndUpdateActions(finalPosition);
        audioState.getPlayer().playAt(finalPosition);
        eventBus.publish(new FocusRequestedEvent(FocusRequestedEvent.Component.MAIN_WINDOW));
    }

    private String getActionId() {
        // This would need to be implemented to get the current action's ID
        // For now, we'll use a simple approach based on the action name
        return (String) getValue(Action.NAME);
    }

    private int getShiftAmount(String actionId) {
        if (actionId.contains("Large")) {
            return preferencesManager.getInt(
                    PreferenceKeys.LARGE_SHIFT, PreferenceKeys.DEFAULT_LARGE_SHIFT);
        } else if (actionId.contains("Medium")) {
            return preferencesManager.getInt(
                    PreferenceKeys.MEDIUM_SHIFT, PreferenceKeys.DEFAULT_MEDIUM_SHIFT);
        } else {
            return preferencesManager.getInt(
                    PreferenceKeys.SMALL_SHIFT, PreferenceKeys.DEFAULT_SMALL_SHIFT);
        }
    }

    private boolean isForwardDirection(String actionId) {
        return actionId.contains("Forward");
    }

    /** A <code>SeekAction</code> should be enabled only when audio is open and not playing. */
    @Override
    public void update() {
        if (audioState.audioOpen()) {
            if (audioState.getPlayer().getStatus() == AudioPlayer.Status.PLAYING) {
                setEnabled(false);
            } else {
                setEnabled(true);
            }
        } else {
            setEnabled(false);
        }
    }
}
