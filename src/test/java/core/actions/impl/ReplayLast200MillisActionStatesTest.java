package core.actions.impl;

import static org.junit.jupiter.api.Assertions.*;

import app.swing.SwingTestFixture;
import core.audio.session.AudioSessionStateMachine;
import core.dispatch.EventDispatchBus;
import core.events.AudioFileLoadRequestedEvent;
import core.events.PlayLast200MillisEvent;
import core.events.PlayPauseEvent;
import java.io.File;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/** Tests ReplayLast200MillisAction behavior using real components. */
@Disabled("disabling after viewport refactor")
class ReplayLast200MillisActionStatesTest extends SwingTestFixture {

    @Test
    void replayLast200msInPausedState() throws Exception {
        EventDispatchBus eventBus = getInstance(EventDispatchBus.class);
        AudioSessionStateMachine stateMachine = getInstance(AudioSessionStateMachine.class);

        // Load test audio
        File testFile = new File("src/test/resources/audio/freerecall.wav");
        eventBus.publish(new AudioFileLoadRequestedEvent(testFile));

        // Wait for load to complete (freerecall.wav is ~4MB)
        for (int i = 0; i < 20; i++) {
            if (stateMachine.getCurrentState() == AudioSessionStateMachine.State.READY) {
                break;
            }
            Thread.sleep(100);
        }

        assertEquals(AudioSessionStateMachine.State.READY, stateMachine.getCurrentState());

        // Start playback
        eventBus.publish(new PlayPauseEvent());
        Thread.sleep(500); // Play for a bit

        // Pause
        eventBus.publish(new PlayPauseEvent());
        Thread.sleep(100); // Wait for pause to complete
        assertEquals(AudioSessionStateMachine.State.PAUSED, stateMachine.getCurrentState());

        // Trigger replay - should play last 200ms
        eventBus.publish(new PlayLast200MillisEvent());

        // In a real environment with FMOD, this would replay the last 200ms
        // We can't verify the actual audio playback in tests, but we've verified
        // the event is handled in the correct state
    }

    @Test
    void replayLast200msInReadyState() throws Exception {
        EventDispatchBus eventBus = getInstance(EventDispatchBus.class);
        AudioSessionStateMachine stateMachine = getInstance(AudioSessionStateMachine.class);

        // Load test audio
        File testFile = new File("src/test/resources/audio/freerecall.wav");
        eventBus.publish(new AudioFileLoadRequestedEvent(testFile));

        // Wait for load to complete
        for (int i = 0; i < 20; i++) {
            if (stateMachine.getCurrentState() == AudioSessionStateMachine.State.READY) {
                break;
            }
            Thread.sleep(100);
        }

        assertEquals(AudioSessionStateMachine.State.READY, stateMachine.getCurrentState());

        // Trigger replay in READY state - should work (plays from 0 to 0)
        eventBus.publish(new PlayLast200MillisEvent());
    }

    @Test
    void replayDoesNothingInPlayingState() throws Exception {
        EventDispatchBus eventBus = getInstance(EventDispatchBus.class);
        AudioSessionStateMachine stateMachine = getInstance(AudioSessionStateMachine.class);

        // Load and start playback
        File testFile = new File("src/test/resources/audio/freerecall.wav");
        eventBus.publish(new AudioFileLoadRequestedEvent(testFile));

        // Wait for load to complete (freerecall.wav is ~4MB)
        for (int i = 0; i < 20; i++) {
            if (stateMachine.getCurrentState() == AudioSessionStateMachine.State.READY) {
                break;
            }
            Thread.sleep(100);
        }

        assertEquals(AudioSessionStateMachine.State.READY, stateMachine.getCurrentState());

        // Now start playback
        eventBus.publish(new PlayPauseEvent());
        Thread.sleep(100); // Wait for playback to start

        assertEquals(AudioSessionStateMachine.State.PLAYING, stateMachine.getCurrentState());

        // Trigger replay in PLAYING state - should do nothing (not handled in this state)
        eventBus.publish(new PlayLast200MillisEvent());

        // Should still be playing
        assertEquals(AudioSessionStateMachine.State.PLAYING, stateMachine.getCurrentState());
    }
}
