package actions;

import env.PreferenceKeys;
import events.AppStateChangedEvent;
import events.AudioSeekRequestedEvent;
import events.EventDispatchBus;
import events.FocusRequestedEvent;
import events.Subscribe;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.ActionEvent;
import javax.swing.AbstractButton;
import lombok.NonNull;
import state.AudioSessionStateMachine;
import state.WaveformSessionDataSource;
import ui.preferences.PreferencesManager;

/**
 * Sets the audio position forward/backward by a pre-defined amount, in response to user request.
 */
@Singleton
public class SeekAction extends BaseAction {

    private final EventDispatchBus eventBus;
    private final PreferencesManager preferencesManager;
    private final WaveformSessionDataSource sessionDataSource;
    private AudioSessionStateMachine.State currentState = AudioSessionStateMachine.State.NO_AUDIO;

    @Inject
    public SeekAction(
            EventDispatchBus eventBus,
            PreferencesManager preferencesManager,
            WaveformSessionDataSource sessionDataSource) {
        super("Seek", "Seek audio position");
        this.eventBus = eventBus;
        this.preferencesManager = preferencesManager;
        this.sessionDataSource = sessionDataSource;
        eventBus.subscribe(this);
    }

    /**
     * Performs the <code>SeekAction</code>, intelligently boundaries to make sure the player isn't
     * taken outside of the audio data.
     */
    @Override
    protected void performAction(ActionEvent e) {
        // Get the menu item text to determine direction and amount
        String actionText = "";
        if (e.getSource() instanceof AbstractButton) {
            actionText = ((AbstractButton) e.getSource()).getText();
        }

        int shiftMillis = getShiftAmount(actionText);
        boolean forward = isForwardDirection(actionText);

        // Work directly with frames to avoid precision loss
        sessionDataSource
                .getPlaybackPositionFrames()
                .ifPresent(
                        currentPositionFrames -> {
                            sessionDataSource
                                    .getSampleRate()
                                    .ifPresent(
                                            sampleRate -> {
                                                sessionDataSource
                                                        .getTotalFrames()
                                                        .ifPresent(
                                                                totalFrames -> {
                                                                    // Calculate shift in frames
                                                                    // directly
                                                                    long shiftFrames =
                                                                            (long)
                                                                                    ((shiftMillis
                                                                                                    / 1000.0)
                                                                                            * sampleRate);
                                                                    long targetFrame =
                                                                            forward
                                                                                    ? currentPositionFrames
                                                                                            + shiftFrames
                                                                                    : currentPositionFrames
                                                                                            - shiftFrames;

                                                                    // Ensure within bounds
                                                                    targetFrame =
                                                                            Math.max(
                                                                                    0,
                                                                                    Math.min(
                                                                                            targetFrame,
                                                                                            totalFrames
                                                                                                    - 1));

                                                                    eventBus.publish(
                                                                            new AudioSeekRequestedEvent(
                                                                                    targetFrame));
                                                                    eventBus.publish(
                                                                            new FocusRequestedEvent(
                                                                                    FocusRequestedEvent
                                                                                            .Component
                                                                                            .MAIN_WINDOW));
                                                                });
                                            });
                        });
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

    @Subscribe
    public void onStateChanged(@NonNull AppStateChangedEvent event) {
        currentState = event.getNewState();
        updateActionState();
    }

    private void updateActionState() {
        // Enable when audio is loaded but not playing
        switch (currentState) {
            case READY, PAUSED -> setEnabled(true);
            default -> setEnabled(false);
        }
    }
}
