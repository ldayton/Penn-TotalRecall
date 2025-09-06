package actions;

import events.AppStateChangedEvent;
import events.EventDispatchBus;
import events.FocusRequestedEvent;
import events.ReplayLast200MillisRequestedEvent;
import events.Subscribe;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.ActionEvent;
import lombok.NonNull;
import state.AudioSessionStateMachine;
import state.WaveformSessionDataSource;

/**
 * Replays the last 200 milliseconds so the annotator can judge whether a word onset has been
 * crossed.
 */
@Singleton
public class ReplayLast200MillisAction extends BaseAction {

    public static final int DURATION_MS = 200;

    private final EventDispatchBus eventBus;
    private final WaveformSessionDataSource sessionSource;
    private AudioSessionStateMachine.State currentState = AudioSessionStateMachine.State.NO_AUDIO;

    @Inject
    public ReplayLast200MillisAction(
            EventDispatchBus eventBus, WaveformSessionDataSource sessionSource) {
        super("Replay Last 200ms", "Replay the last 200 milliseconds of audio");
        this.eventBus = eventBus;
        this.sessionSource = sessionSource;
        eventBus.subscribe(this);
    }

    @Override
    protected void performAction(ActionEvent e) {
        eventBus.publish(new ReplayLast200MillisRequestedEvent());
        eventBus.publish(new FocusRequestedEvent(FocusRequestedEvent.Component.MAIN_WINDOW));
    }

    @Subscribe
    public void onStateChanged(@NonNull AppStateChangedEvent event) {
        currentState = event.getNewState();
        updateActionState();
    }

    private void updateActionState() {
        // User can replay last 200 millis when audio is open, not playing, and not at the beginning
        if (currentState == AudioSessionStateMachine.State.READY
                || currentState == AudioSessionStateMachine.State.PAUSED) {
            // Check if we're not at the beginning
            double position = sessionSource.getPlaybackPosition().orElse(0.0);
            setEnabled(position > 0.2); // Enable if we're past 200ms
        } else {
            setEnabled(false);
        }
    }

    /**
     * User can replay last 200 millis when audio is open, not playing, and not on the first frame.
     */
    @Override
    public void update() {
        // No-op - now using event-driven updates via @Subscribe to AppStateChangedEvent
    }
}
