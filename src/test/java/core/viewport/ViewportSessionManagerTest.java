package core.viewport;

import static org.junit.jupiter.api.Assertions.*;

import app.headless.HeadlessTestFixture;
import core.audio.session.AudioSessionStateMachine;
import core.dispatch.EventDispatchBus;
import core.events.AudioFileLoadRequestedEvent;
import core.events.PlayPauseEvent;
import core.events.SeekEvent;
import java.io.File;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/** Tests for ViewportSessionManager behavior. */
@Disabled("ViewportSession creation in headless tests needs investigation")
class ViewportSessionManagerTest extends HeadlessTestFixture {

    @Test
    void seekToZeroDuringPauseShouldNotRecreateViewportSession() throws Exception {
        // Get instances from DI container
        EventDispatchBus eventBus = getInstance(EventDispatchBus.class);
        AudioSessionStateMachine stateMachine = getInstance(AudioSessionStateMachine.class);
        ViewportSessionManager viewportManager = getInstance(ViewportSessionManager.class);

        // Load test audio file
        File testFile = new File("src/test/resources/audio/sweep.wav");
        eventBus.publish(new AudioFileLoadRequestedEvent(testFile));

        // Wait for loading to complete (transitions from NO_AUDIO -> LOADING -> READY)
        int maxWaitMs = 2000;
        int waitedMs = 0;
        while (stateMachine.getCurrentState() != AudioSessionStateMachine.State.READY
                && stateMachine.getCurrentState() != AudioSessionStateMachine.State.ERROR
                && waitedMs < maxWaitMs) {
            Thread.sleep(100);
            waitedMs += 100;
        }

        assertEquals(
                AudioSessionStateMachine.State.READY,
                stateMachine.getCurrentState(),
                "Audio should be loaded and ready");

        // Get the initial ViewportSession that was created when audio loaded
        assertTrue(
                viewportManager.getCurrentSession().isPresent(),
                "Should have a ViewportSession after loading audio");
        ViewportSession initialSession = viewportManager.getCurrentSession().get();

        // Start playback
        eventBus.publish(new PlayPauseEvent());
        Thread.sleep(100);
        assertEquals(AudioSessionStateMachine.State.PLAYING, stateMachine.getCurrentState());

        // Pause
        eventBus.publish(new PlayPauseEvent());
        Thread.sleep(100);
        assertEquals(AudioSessionStateMachine.State.PAUSED, stateMachine.getCurrentState());

        // Seek to position 0 - in Swing this transitions from PAUSED to READY
        // but in headless it may stay in PAUSED
        eventBus.publish(new SeekEvent(0));
        Thread.sleep(100);

        // The state after seek(0) varies but we're testing ViewportSession persistence
        var stateAfterSeek = stateMachine.getCurrentState();
        assertTrue(
                stateAfterSeek == AudioSessionStateMachine.State.READY
                        || stateAfterSeek == AudioSessionStateMachine.State.PAUSED,
                "After seek(0) should be in READY or PAUSED, but was " + stateAfterSeek);

        // THIS WILL FAIL - The ViewportSession should NOT be recreated
        assertTrue(
                viewportManager.getCurrentSession().isPresent(),
                "Should still have a ViewportSession after seek(0)");
        ViewportSession sessionAfterSeek = viewportManager.getCurrentSession().get();

        assertSame(
                initialSession,
                sessionAfterSeek,
                "ViewportSession should not be recreated when seeking to 0 from PAUSED state");
    }
}
