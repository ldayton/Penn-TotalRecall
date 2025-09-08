package core.actions;

import core.dispatch.EventDispatchBus;
import core.dispatch.Subscribe;
import core.events.AppStateChangedEvent;
import core.events.FocusEvent;
import core.events.PlayLast200MillisEvent;
import core.state.AudioSessionStateMachine;
import core.state.WaveformSessionDataSource;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.NonNull;

/**
 * Replays the last 200 milliseconds so the annotator can judge whether a word onset has been
 * crossed.
 */
@Singleton
public class ReplayLast200MillisAction extends Action {

    public static final int DURATION_MS = 200;

    private final EventDispatchBus eventBus;
    private final WaveformSessionDataSource sessionSource;
    private AudioSessionStateMachine.State currentState = AudioSessionStateMachine.State.NO_AUDIO;

    @Inject
    public ReplayLast200MillisAction(
            EventDispatchBus eventBus, WaveformSessionDataSource sessionSource) {
        this.eventBus = eventBus;
        this.sessionSource = sessionSource;
        eventBus.subscribe(this);
    }

    @Override
    public void execute() {
        eventBus.publish(new PlayLast200MillisEvent());
        eventBus.publish(new FocusEvent(FocusEvent.Component.MAIN_WINDOW));
    }

    @Override
    public boolean isEnabled() {
        // User can replay last 200 millis when audio is open, not playing, and not at the beginning
        if (currentState == AudioSessionStateMachine.State.READY
                || currentState == AudioSessionStateMachine.State.PAUSED) {
            // Check if we're not at the beginning
            double position = sessionSource.getPlaybackPosition().orElse(0.0);
            return position > 0.2; // Enable if we're past 200ms
        }
        return false;
    }

    @Override
    public String getLabel() {
        return "Replay Last 200ms";
    }

    @Override
    public String getTooltip() {
        return "Replay the last 200 milliseconds of audio";
    }

    @Subscribe
    public void onStateChanged(@NonNull AppStateChangedEvent event) {
        currentState = event.newState();
        notifyObservers();
    }
}
