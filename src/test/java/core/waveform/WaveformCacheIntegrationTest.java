package core.waveform;

import static org.junit.jupiter.api.Assertions.*;

import app.headless.HeadlessTestFixture;
import core.audio.AudioEngine;
import core.audio.session.AudioSessionDataSource;
import core.audio.session.AudioSessionStateMachine;
import core.dispatch.EventDispatchBus;
import core.dispatch.Subscribe;
import core.events.AppStateChangedEvent;
import core.events.AudioFileLoadRequestedEvent;
import core.viewport.ViewportSessionManager;
import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import ui.viewport.ViewportPainter;

/** Integration test for waveform cache behavior across application state changes. */
@Slf4j
class WaveformCacheIntegrationTest extends HeadlessTestFixture {

    private static final String SAMPLE_WAV_PATH = "src/test/resources/audio/freerecall.wav";
    private static final int STATE_CHANGE_TIMEOUT_SECONDS = 5;

    @Test
    @Timeout(10)
    void testCacheIsClearedOnLoadingState() throws Exception {
        // Get required instances
        CacheStats cacheStats = getInstance(CacheStats.class);
        AudioSessionStateMachine stateMachine = getInstance(AudioSessionStateMachine.class);
        AudioSessionDataSource sessionSource = getInstance(AudioSessionDataSource.class);
        EventDispatchBus eventBus = getInstance(EventDispatchBus.class);
        AudioEngine audioEngine = getInstance(AudioEngine.class);

        // Verify initial state
        assertEquals(
                AudioSessionStateMachine.State.NO_AUDIO,
                stateMachine.getCurrentState(),
                "Should start in NO_AUDIO state");

        // Reset cache stats to ensure clean test
        cacheStats.reset();
        assertEquals(0, cacheStats.getRequests(), "Cache should start with zero requests");

        // Create a listener to wait for LOADING state
        CompletableFuture<AppStateChangedEvent> loadingStateFuture = new CompletableFuture<>();
        StateListener stateListener = new StateListener(loadingStateFuture);
        eventBus.subscribe(stateListener);

        // Simulate some cache activity before loading
        // This would normally be done through WaveformSegmentCache, but for testing
        // we can directly manipulate the stats
        cacheStats.recordRequest();
        cacheStats.recordHit();
        cacheStats.recordRequest();
        cacheStats.recordMiss();
        assertEquals(2, cacheStats.getRequests(), "Should have 2 requests before loading");
        assertEquals(1, cacheStats.getHits(), "Should have 1 hit before loading");
        assertEquals(1, cacheStats.getMisses(), "Should have 1 miss before loading");

        // Trigger audio loading through event
        File audioFile = new File(SAMPLE_WAV_PATH);
        log.info("Loading audio file: {}", audioFile.getAbsolutePath());

        // Transition to LOADING state
        stateMachine.transitionToLoading();

        // Wait for LOADING state event to be processed
        Thread.sleep(100);

        // Give a moment for cache stats to process the event
        Thread.sleep(100);

        // Verify cache was cleared
        assertEquals(
                0, cacheStats.getRequests(), "Cache requests should be reset to 0 after LOADING");
        assertEquals(0, cacheStats.getHits(), "Cache hits should be reset to 0 after LOADING");
        assertEquals(0, cacheStats.getMisses(), "Cache misses should be reset to 0 after LOADING");
        assertEquals(0, cacheStats.getPuts(), "Cache puts should be reset to 0 after LOADING");
        assertEquals(
                0, cacheStats.getEvictions(), "Cache evictions should be reset to 0 after LOADING");

        log.info("Cache successfully cleared on LOADING state");

        // Clean up
        eventBus.unsubscribe(stateListener);
    }

    /** Helper class to listen for state changes. */
    public static class StateListener {
        private final CompletableFuture<AppStateChangedEvent> stateFuture;

        StateListener(CompletableFuture<AppStateChangedEvent> stateFuture) {
            this.stateFuture = stateFuture;
        }

        @Subscribe
        public void onStateChanged(@NonNull AppStateChangedEvent event) {
            log.debug("Received state change event: {}", event);
            if (event.newState() == AudioSessionStateMachine.State.LOADING
                    && !stateFuture.isDone()) {
                stateFuture.complete(event);
            }
        }
    }

    @Test
    @Timeout(20)
    void testNoCacheMissesDuringPlayback() throws Exception {
        // Get required instances
        CacheStats cacheStats = getInstance(CacheStats.class);
        AudioSessionStateMachine stateMachine = getInstance(AudioSessionStateMachine.class);
        AudioSessionDataSource sessionSource = getInstance(AudioSessionDataSource.class);
        EventDispatchBus eventBus = getInstance(EventDispatchBus.class);
        AudioEngine audioEngine = getInstance(AudioEngine.class);
        ViewportSessionManager viewportManager = getInstance(ViewportSessionManager.class);

        // Verify initial state
        assertEquals(
                AudioSessionStateMachine.State.NO_AUDIO,
                stateMachine.getCurrentState(),
                "Should start in NO_AUDIO state");

        // Reset cache stats
        cacheStats.reset();

        // Create listeners for state changes
        CompletableFuture<AppStateChangedEvent> readyStateFuture = new CompletableFuture<>();
        CompletableFuture<AppStateChangedEvent> playingStateFuture = new CompletableFuture<>();
        PlaybackStateListener stateListener =
                new PlaybackStateListener(readyStateFuture, playingStateFuture);
        eventBus.subscribe(stateListener);

        // Load audio file
        File audioFile = new File(SAMPLE_WAV_PATH);
        log.info("Loading audio file for playback test: {}", audioFile.getAbsolutePath());

        // Publish audio load request event
        eventBus.publish(new AudioFileLoadRequestedEvent(audioFile));

        // Wait for READY state
        AppStateChangedEvent readyEvent =
                readyStateFuture.get(STATE_CHANGE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertNotNull(readyEvent, "Should receive READY state event");
        assertEquals(
                AudioSessionStateMachine.State.READY,
                readyEvent.newState(),
                "Should be in READY state");

        // Define viewport dimensions for rendering (x, y, width, height)
        ScreenDimension viewportBounds = new ScreenDimension(0, 0, 1000, 200);

        // Warm up the cache with initial render
        log.info("Warming up cache with initial render");
        log.debug("Viewport: 1000px wide × 200px high");
        log.debug("Segment width: 200px, Expected visible segments: 5");
        log.debug("Prefetch count: 2 each direction");

        // Get the render spec which will trigger caching
        var renderSpec = viewportManager.getRenderSpec(viewportBounds);
        log.debug("Render spec mode: {}", renderSpec.mode());
        log.debug(
                "Cache before warmup - Requests: {}, Hits: {}, Misses: {}",
                cacheStats.getRequests(),
                cacheStats.getHits(),
                cacheStats.getMisses());

        // Wait for render to complete and cache to stabilize
        Thread.sleep(2000);

        log.debug(
                "Cache after warmup - Requests: {}, Hits: {}, Misses: {}",
                cacheStats.getRequests(),
                cacheStats.getHits(),
                cacheStats.getMisses());
        log.debug("Total segments loaded: {}", cacheStats.getMisses());

        // Record cache state after warmup
        long initialRequests = cacheStats.getRequests();
        long initialHits = cacheStats.getHits();
        long initialMisses = cacheStats.getMisses();
        log.info(
                "Cache state after warmup - Requests: {}, Hits: {}, Misses: {}",
                initialRequests,
                initialHits,
                initialMisses);

        // Start playback
        log.info("Starting playback");
        assertTrue(
                stateMachine.compareAndSetState(
                        AudioSessionStateMachine.State.READY,
                        AudioSessionStateMachine.State.PLAYING),
                "Should transition to PLAYING state");

        // Fire playing state event
        eventBus.publish(
                new AppStateChangedEvent(
                        AudioSessionStateMachine.State.READY,
                        AudioSessionStateMachine.State.PLAYING,
                        0L)); // Start position

        // Wait for PLAYING state confirmation
        AppStateChangedEvent playingEvent =
                playingStateFuture.get(STATE_CHANGE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertNotNull(playingEvent, "Should receive PLAYING state event");

        // Monitor cache misses for 10 seconds with simulated render cycle
        log.info(
                "Monitoring cache misses during playback for 10 seconds at {} FPS",
                ViewportPainter.FPS);
        long lastMissCount = initialMisses; // Start from post-warmup miss count

        int millisPerFrame = 1000 / ViewportPainter.FPS;
        int framesPerSecond = ViewportPainter.FPS;

        for (int second = 1; second <= 10; second++) {
            // Simulate render cycle at 30 FPS
            for (int frame = 0; frame < framesPerSecond; frame++) {
                // Trigger waveform rendering
                viewportManager.getRenderSpec(viewportBounds);
                Thread.sleep(millisPerFrame);
            }

            long currentRequests = cacheStats.getRequests();
            long currentHits = cacheStats.getHits();
            long currentMisses = cacheStats.getMisses();
            long newMisses = currentMisses - lastMissCount;

            log.debug(
                    "Second {}: Requests: {} (+{}), Hits: {} (+{}), Misses: {} (+{}), Hit Rate:"
                            + " {}%",
                    second,
                    currentRequests,
                    currentRequests - initialRequests,
                    currentHits,
                    currentHits - initialHits,
                    currentMisses,
                    newMisses,
                    String.format("%.1f", cacheStats.getHitRate() * 100));

            // Assert no new cache misses after the first second (allow warmup)
            if (second > 1) {
                assertEquals(
                        0,
                        newMisses,
                        String.format(
                                "Should have no cache misses at second %d during playback",
                                second));
            }

            lastMissCount = currentMisses;
        }

        // Stop playback
        log.info("Stopping playback");
        assertTrue(
                stateMachine.compareAndSetState(
                        AudioSessionStateMachine.State.PLAYING,
                        AudioSessionStateMachine.State.READY),
                "Should transition back to READY state");

        // Log final cache statistics
        log.info("Final cache statistics:");
        cacheStats.logStats();

        // Assert that cache was actually used (hits > 0)
        long finalHits = cacheStats.getHits();
        long finalRequests = cacheStats.getRequests();
        assertTrue(
                finalHits > 0,
                String.format(
                        "Cache should have hits during playback, but had %d hits out of %d"
                                + " requests",
                        finalHits, finalRequests));

        // Also assert that we had actual cache activity
        assertTrue(
                finalRequests > initialRequests,
                String.format(
                        "Cache should have new requests during playback. Initial: %d, Final: %d",
                        initialRequests, finalRequests));

        log.info(
                "Final cache summary - Requests: {}, Hits: {}, Misses: {}, Hit rate: {}%",
                finalRequests,
                finalHits,
                cacheStats.getMisses(),
                String.format("%.2f", cacheStats.getHitRate() * 100));
        log.info("Test passed: {} cache hits during playback with no new misses", finalHits);

        // Clean up
        eventBus.unsubscribe(stateListener);
        // Audio will be cleaned up by the session manager
    }

    /** Helper class to listen for playback state changes. */
    public static class PlaybackStateListener {
        private final CompletableFuture<AppStateChangedEvent> readyStateFuture;
        private final CompletableFuture<AppStateChangedEvent> playingStateFuture;

        PlaybackStateListener(
                CompletableFuture<AppStateChangedEvent> readyStateFuture,
                CompletableFuture<AppStateChangedEvent> playingStateFuture) {
            this.readyStateFuture = readyStateFuture;
            this.playingStateFuture = playingStateFuture;
        }

        @Subscribe
        public void onStateChanged(@NonNull AppStateChangedEvent event) {
            log.debug("Received state change event in playback test: {}", event);
            if (event.newState() == AudioSessionStateMachine.State.READY
                    && !readyStateFuture.isDone()) {
                readyStateFuture.complete(event);
            } else if (event.newState() == AudioSessionStateMachine.State.PLAYING
                    && !playingStateFuture.isDone()) {
                playingStateFuture.complete(event);
            }
        }
    }
}
