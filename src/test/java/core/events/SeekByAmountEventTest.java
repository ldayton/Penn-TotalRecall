package core.events;

import static org.junit.jupiter.api.Assertions.*;

import app.headless.HeadlessTestFixture;
import core.audio.session.AudioSessionManager;
import core.audio.session.AudioSessionStateMachine;
import core.dispatch.EventDispatchBus;
import java.io.File;
import org.junit.jupiter.api.Test;

class SeekByAmountEventTest extends HeadlessTestFixture {

    @Test
    void testBackwardSeekActuallyMovesBackward() throws Exception {
        EventDispatchBus eventBus = getInstance(EventDispatchBus.class);
        AudioSessionManager sessionManager = getInstance(AudioSessionManager.class);
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

        // Start playback and let it play for a bit
        eventBus.publish(new PlayPauseEvent());
        Thread.sleep(1000); // Play for 1 second

        // Pause
        eventBus.publish(new PlayPauseEvent());
        Thread.sleep(100);
        assertEquals(AudioSessionStateMachine.State.PAUSED, stateMachine.getCurrentState());

        // Get initial position
        long initialPosition = sessionManager.getPlaybackPositionFrames().orElse(0L);
        assertTrue(initialPosition > 0, "Should have played forward from the start");

        // Seek backward by 100ms
        eventBus.publish(new SeekByAmountEvent(SeekByAmountEvent.Direction.BACKWARD, 100));
        Thread.sleep(100); // Give it time to process

        // Check that position actually moved backward
        long newPosition = sessionManager.getPlaybackPositionFrames().orElse(0L);
        assertTrue(
                newPosition < initialPosition,
                String.format(
                        "Position should have moved backward. Was: %d, Now: %d",
                        initialPosition, newPosition));

        // Try multiple backward seeks to ensure they keep moving backward
        long previousPosition = newPosition;
        for (int i = 0; i < 3; i++) {
            eventBus.publish(new SeekByAmountEvent(SeekByAmountEvent.Direction.BACKWARD, 100));
            Thread.sleep(100);
            newPosition = sessionManager.getPlaybackPositionFrames().orElse(0L);
            assertTrue(
                    newPosition < previousPosition,
                    String.format(
                            "Seek %d: Position should keep moving backward. Was: %d, Now: %d",
                            i + 1, previousPosition, newPosition));
            previousPosition = newPosition;
        }
    }
}
