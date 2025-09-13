package core.events;

import static org.junit.jupiter.api.Assertions.*;

import app.headless.HeadlessTestFixture;
import core.dispatch.EventDispatchBus;
import core.state.AudioSessionManager;
import core.state.AudioSessionStateMachine;
import core.state.ViewportPositionManager;
import java.io.File;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Test that viewport properly follows playback after SeekToStart and replay. */
class SeekToStartViewportMovementTest extends HeadlessTestFixture {

    @Test
    @Disabled("Viewport lag after SeekToStart - needs redesign of viewport update mechanism")
    @DisplayName("Viewport should move within 100ms after second play following SeekToStart")
    void testViewportMovesQuicklyAfterSecondPlay() throws Exception {
        // Get instances from DI container
        EventDispatchBus eventBus = getInstance(EventDispatchBus.class);
        AudioSessionManager sessionManager = getInstance(AudioSessionManager.class);
        AudioSessionStateMachine stateMachine = getInstance(AudioSessionStateMachine.class);
        ViewportPositionManager viewport = getInstance(ViewportPositionManager.class);

        // Set viewport dimensions
        viewport.setWidth(1000);
        viewport.setZoom(200); // 200 pixels per second = 5 seconds visible

        // Load freerecall.wav
        File testFile = new File("src/test/resources/audio/freerecall.wav");
        eventBus.publish(new AudioFileLoadRequestedEvent(testFile));
        Thread.sleep(500); // Wait for load

        // FIRST PLAY: Start playback
        eventBus.publish(new PlayPauseEvent());
        Thread.sleep(100);
        assertEquals(AudioSessionStateMachine.State.PLAYING, stateMachine.getCurrentState());

        // Let it play for a bit
        Thread.sleep(500);

        // Get position after first play
        double firstPlayPosition = sessionManager.getPlaybackPosition().orElse(0.0);
        assertTrue(firstPlayPosition > 0.4, "Should have played for at least 400ms");

        // PAUSE
        eventBus.publish(new PlayPauseEvent());
        Thread.sleep(100);
        assertEquals(AudioSessionStateMachine.State.PAUSED, stateMachine.getCurrentState());

        // SEEK TO START
        eventBus.publish(new SeekToStartEvent());
        Thread.sleep(100);
        assertEquals(AudioSessionStateMachine.State.READY, stateMachine.getCurrentState());

        // Verify viewport is centered at 0 after seek to start
        double viewportAfterSeek = viewport.getRawStartSeconds();
        assertEquals(
                -2.5,
                viewportAfterSeek,
                0.01,
                "Viewport should be centered at position 0 after SeekToStart");

        // SECOND PLAY: This is where the bug occurs
        eventBus.publish(new PlayPauseEvent());

        // Wait just 100ms - viewport should already be moving
        Thread.sleep(100);

        // Debug: verify play state
        assertEquals(
                AudioSessionStateMachine.State.PLAYING,
                stateMachine.getCurrentState(),
                "Should be playing after second play");

        // Get actual playback position after 100ms
        double actualPosition = sessionManager.getPlaybackPosition().orElse(0.0);
        assertTrue(
                actualPosition > 0.05,
                "Playback should have progressed at least 50ms after 100ms wait, but was "
                        + actualPosition);

        // Get viewport position - it should be following the actual playback
        double viewportPosition = viewport.getRawStartSeconds();

        // The viewport should have moved from its initial -2.5 position
        // It should be centered on the actual playback position
        double expectedViewportStart = actualPosition - 2.5; // Centered on actual position

        // Allow some tolerance but viewport should definitely have moved
        double difference = Math.abs(viewportPosition - expectedViewportStart);
        assertTrue(
                difference < 0.1,
                String.format(
                        "Viewport should be following playback after 100ms. Expected viewport at"
                            + " %.3f (centered on playback %.3f), but was at %.3f. Difference: %.3f"
                            + " seconds",
                        expectedViewportStart, actualPosition, viewportPosition, difference));

        // Also verify viewport has moved from its SeekToStart position
        assertTrue(
                viewportPosition > -2.5,
                String.format(
                        "Viewport should have moved from initial position -2.5, but is still at"
                                + " %.3f",
                        viewportPosition));
    }
}
