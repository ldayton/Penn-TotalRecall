package core.audio.fmod;

import static org.junit.jupiter.api.Assertions.*;

import core.audio.AudioHandle;
import core.audio.AudioMetadata;
import core.audio.PlaybackHandle;
import core.audio.PlaybackListener;
import core.audio.PlaybackState;
import core.audio.exceptions.AudioLoadException;
import core.audio.exceptions.AudioPlaybackException;
import core.env.Platform;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Integration tests for FmodAudioEngine. Tests the orchestration of all FMOD components together
 * without mocks to verify real behavior and catch integration-specific bugs.
 */
@Slf4j
@Tag("audio")
class FmodAudioEngineTest {

    private FmodAudioEngine engine;
    private FmodSystemManager systemManager;
    private FmodAudioLoadingManager loadingManager;
    private FmodPlaybackManager playbackManager;
    private FmodListenerManager listenerManager;
    private FmodSystemStateManager stateManager;
    private FmodHandleLifecycleManager lifecycleManager;

    private static final String SAMPLE_WAV = "src/test/resources/audio/freerecall.wav";
    private static final String SWEEP_WAV = "src/test/resources/audio/sweep.wav";
    private static final int PROGRESS_INTERVAL_MS = 50;

    @BeforeEach
    void setUp() {
        // Create all real components - no mocks
        stateManager = new FmodSystemStateManager();
        systemManager =
                new FmodSystemManager(
                        new FmodLibraryLoader(
                                new FmodProperties("unpackaged", "standard"), new Platform()));
        lifecycleManager = new FmodHandleLifecycleManager();

        // Initialize the system manager first to load FMOD
        systemManager.initialize();

        loadingManager =
                new FmodAudioLoadingManager(
                        systemManager.getSystem(), stateManager, lifecycleManager);
        playbackManager = new FmodPlaybackManager(systemManager.getSystem());
        listenerManager = new FmodListenerManager(systemManager.getSystem(), PROGRESS_INTERVAL_MS);

        // Create the engine with real components
        engine =
                new FmodAudioEngine(
                        systemManager,
                        loadingManager,
                        playbackManager,
                        listenerManager,
                        stateManager,
                        lifecycleManager);
    }

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.close();
        }
    }

    // ========== Single Audio Restriction Tests ==========

    @Test
    @DisplayName("Should enforce single audio file restriction")
    void testSingleAudioRestriction() throws Exception {
        // Load first audio
        AudioHandle handle1 = engine.loadAudio(SAMPLE_WAV);
        assertNotNull(handle1);
        AudioMetadata metadata1 = engine.getMetadata(handle1);
        assertNotNull(metadata1);

        // Load second audio - should replace first
        AudioHandle handle2 = engine.loadAudio(SWEEP_WAV);
        assertNotNull(handle2);
        AudioMetadata metadata2 = engine.getMetadata(handle2);
        assertNotNull(metadata2);

        // First handle should no longer work
        assertFalse(((FmodAudioHandle) handle1).isValid());
        assertThrows(AudioPlaybackException.class, () -> engine.getMetadata(handle1));
        assertThrows(AudioPlaybackException.class, () -> engine.play(handle1));

        // Second handle should work
        PlaybackHandle playback = engine.play(handle2);
        assertNotNull(playback);
        assertTrue(engine.isPlaying(playback));
        engine.stop(playback);
    }

    @Test
    @DisplayName("Should reject playback with stale handle after reload")
    void testStaleHandleRejection() throws Exception {
        log.warn("=== EXPECTED WARNING FOLLOWS - Testing stale handle cleanup ===");
        AudioHandle handle1 = engine.loadAudio(SAMPLE_WAV);
        PlaybackHandle playback1 = engine.play(handle1);
        engine.pause(playback1);

        // Load new audio while playback is paused
        AudioHandle handle2 = engine.loadAudio(SWEEP_WAV);

        // Old playback handle should be invalid
        assertFalse(engine.isPaused(playback1));
        assertTrue(engine.isStopped(playback1));

        // Operations on old handle should fail
        assertThrows(AudioPlaybackException.class, () -> engine.play(handle1));
        assertThrows(AudioPlaybackException.class, () -> engine.resume(playback1));

        // New handle should work
        PlaybackHandle playback2 = engine.play(handle2);
        assertTrue(engine.isPlaying(playback2));
        engine.stop(playback2);
    }

    // ========== Single Playback Restriction Tests ==========

    @Test
    @DisplayName("Should enforce single active playback")
    void testSinglePlaybackRestriction() throws Exception {
        AudioHandle handle = engine.loadAudio(SAMPLE_WAV);

        // Start first playback
        PlaybackHandle playback1 = engine.play(handle);
        assertTrue(engine.isPlaying(playback1));
        long pos1 = engine.getPosition(playback1);

        // Cannot start second playback with same handle
        assertThrows(AudioPlaybackException.class, () -> engine.play(handle));

        // First playback should still be active
        assertTrue(engine.isPlaying(playback1));
        Thread.sleep(100);
        long pos2 = engine.getPosition(playback1);
        assertTrue(pos2 > pos1, "Playback should be progressing");

        engine.stop(playback1);
    }

    @Test
    @DisplayName("Should invalidate old playback when starting new one after stop")
    void testPlaybackHandleInvalidation() throws Exception {
        AudioHandle handle = engine.loadAudio(SAMPLE_WAV);

        PlaybackHandle playback1 = engine.play(handle);
        engine.stop(playback1);
        assertTrue(engine.isStopped(playback1));

        // Start new playback
        PlaybackHandle playback2 = engine.play(handle);
        assertTrue(engine.isPlaying(playback2));

        // Old handle operations should fail gracefully
        assertFalse(engine.isPlaying(playback1));
        assertEquals(0L, engine.getPosition(playback1));

        engine.stop(playback2);
    }

    // ========== Playback Lifecycle Tests ==========

    @Test
    @DisplayName("Should handle complete playback lifecycle")
    @Timeout(5)
    void testFullPlaybackLifecycle() throws Exception {
        TestListener listener = new TestListener();
        engine.addPlaybackListener(listener);

        AudioHandle handle = engine.loadAudio(SAMPLE_WAV);
        AudioMetadata metadata = engine.getMetadata(handle);
        assertTrue(metadata.frameCount() > 0);

        // Play
        PlaybackHandle playback = engine.play(handle);
        assertTrue(engine.isPlaying(playback));
        assertEquals(PlaybackState.PLAYING, engine.getState(playback));
        // State already changed, just verify it was recorded
        assertTrue(listener.stateChanges.contains(PlaybackState.PLAYING));

        // Pause
        engine.pause(playback);
        assertTrue(engine.isPaused(playback));
        assertEquals(PlaybackState.PAUSED, engine.getState(playback));
        // State already changed, just verify it was recorded
        assertTrue(listener.stateChanges.contains(PlaybackState.PAUSED));
        long pausePos = engine.getPosition(playback);

        // Verify position doesn't change while paused
        Thread.sleep(100);
        assertEquals(
                pausePos, engine.getPosition(playback), "Position should not change while paused");

        // Resume
        engine.resume(playback);
        assertTrue(engine.isPlaying(playback));
        assertEquals(PlaybackState.PLAYING, engine.getState(playback));

        // Seek
        long seekTarget = metadata.frameCount() / 2;
        engine.seek(playback, seekTarget);
        Thread.sleep(50); // Give FMOD time to process seek
        long actualFrames = engine.getPosition(playback);
        // Allow some tolerance for seek (within 3000 frames)
        assertTrue(
                Math.abs(actualFrames - seekTarget) < 3000,
                "Seek position should be close to target");

        // Stop
        engine.stop(playback);
        assertTrue(engine.isStopped(playback));
        assertEquals(PlaybackState.STOPPED, engine.getState(playback));
        // State already changed, just verify it was recorded
        assertTrue(listener.stateChanges.contains(PlaybackState.STOPPED));

        // Verify listener received all state changes
        assertTrue(listener.stateChanges.contains(PlaybackState.PLAYING));
        assertTrue(listener.stateChanges.contains(PlaybackState.PAUSED));
        assertTrue(listener.stateChanges.contains(PlaybackState.STOPPED));
    }

    @Test
    @DisplayName("Should receive progress updates during playback")
    @Timeout(3)
    void testProgressUpdates() throws Exception {
        TestListener listener = new TestListener();
        engine.addPlaybackListener(listener);

        AudioHandle handle = engine.loadAudio(SAMPLE_WAV);
        PlaybackHandle playback = engine.play(handle);

        // Wait for progress updates
        assertTrue(listener.waitForProgress(5, 2, TimeUnit.SECONDS));

        // Progress should be increasing
        List<Long> positions = listener.getProgressPositions();
        for (int i = 1; i < positions.size(); i++) {
            assertTrue(
                    positions.get(i) >= positions.get(i - 1),
                    "Progress should increase or stay same");
        }

        engine.stop(playback);
    }

    // ========== Error Recovery Tests ==========

    @Test
    @DisplayName("Should handle operations on closed engine")
    void testOperationsOnClosedEngine() throws Exception {
        AudioHandle handle = engine.loadAudio(SAMPLE_WAV);
        PlaybackHandle playback = engine.play(handle);

        engine.close();

        // All operations should fail gracefully
        assertThrows(Exception.class, () -> engine.loadAudio(SWEEP_WAV));
        assertThrows(Exception.class, () -> engine.play(handle));
        assertThrows(Exception.class, () -> engine.pause(playback));
        assertThrows(Exception.class, () -> engine.getMetadata(handle));
        assertDoesNotThrow(() -> engine.close()); // Double close should be safe
    }

    @Test
    @DisplayName("Should recover from failed load operations")
    void testFailedLoadRecovery() throws Exception {
        // Try to load non-existent file
        assertThrows(AudioLoadException.class, () -> engine.loadAudio("nonexistent.wav"));

        // Engine should still be operational
        AudioHandle handle = engine.loadAudio(SAMPLE_WAV);
        assertNotNull(handle);
        PlaybackHandle playback = engine.play(handle);
        assertTrue(engine.isPlaying(playback));
        engine.stop(playback);
    }

    @Test
    @DisplayName("Should handle seek beyond audio bounds")
    void testSeekBeyondBounds() throws Exception {
        AudioHandle handle = engine.loadAudio(SAMPLE_WAV);
        AudioMetadata metadata = engine.getMetadata(handle);
        PlaybackHandle playback = engine.play(handle);

        // Seek beyond end
        engine.seek(playback, metadata.frameCount() * 2);

        // Playback might stop or clamp to end
        Thread.sleep(100);
        long pos = engine.getPosition(playback);
        long maxFrames = metadata.frameCount();
        assertTrue(pos <= maxFrames, "Position should not exceed audio length in frames");

        // Seek to negative should fail
        assertThrows(AudioPlaybackException.class, () -> engine.seek(playback, -1));

        engine.stop(playback);
    }

    // ========== Resource Management Tests ==========

    @Test
    @DisplayName("Should properly clean up resources on audio replacement")
    void testResourceCleanupOnReload() throws Exception {
        TestListener listener = new TestListener();
        engine.addPlaybackListener(listener);

        // Rapid load/unload cycles
        for (int i = 0; i < 10; i++) {
            String file = (i % 2 == 0) ? SAMPLE_WAV : SWEEP_WAV;
            AudioHandle handle = engine.loadAudio(file);
            PlaybackHandle playback = engine.play(handle);
            Thread.sleep(20);
            engine.stop(playback);
        }

        // Load and play one more time to verify engine still works
        AudioHandle handle = engine.loadAudio(SAMPLE_WAV);
        PlaybackHandle playback = engine.play(handle);
        assertTrue(engine.isPlaying(playback));
        engine.stop(playback);

        // Should have received state changes for all playbacks
        // Note: onPlaybackComplete is only for natural completion, not manual stop()
        // Each playback generates PLAYING and STOPPED state changes
        assertTrue(
                listener.stateChanges.size() >= 20,
                "Expected at least 20 state changes (PLAYING + STOPPED for each of 10 playbacks),"
                        + " got "
                        + listener.stateChanges.size());
    }

    @Test
    @DisplayName("Should close cleanly during active playback")
    @Timeout(3)
    void testCloseeDuringPlayback() throws Exception {
        AudioHandle handle = engine.loadAudio(SAMPLE_WAV);
        PlaybackHandle playback = engine.play(handle);
        assertTrue(engine.isPlaying(playback));

        // Close while playing - should not hang or crash
        assertDoesNotThrow(() -> engine.close());

        // Engine should be closed
        assertThrows(Exception.class, () -> engine.loadAudio(SWEEP_WAV));
    }

    // ========== Concurrent Operations Tests ==========

    @Test
    @DisplayName("Should handle concurrent control operations safely")
    @Timeout(5)
    void testConcurrentControlOperations() throws Exception {
        AudioHandle handle = engine.loadAudio(SAMPLE_WAV);
        PlaybackHandle playback = engine.play(handle);

        AtomicBoolean error = new AtomicBoolean(false);
        CyclicBarrier barrier = new CyclicBarrier(4);
        ExecutorService executor = Executors.newFixedThreadPool(4);

        // Multiple threads trying different operations
        List<Future<?>> futures = new ArrayList<>();

        // Thread 1: Pause/Resume
        futures.add(
                executor.submit(
                        () -> {
                            try {
                                barrier.await();
                                for (int i = 0; i < 20; i++) {
                                    engine.pause(playback);
                                    Thread.sleep(10);
                                    engine.resume(playback);
                                    Thread.sleep(10);
                                }
                            } catch (Exception e) {
                                if (!(e instanceof AudioPlaybackException)) {
                                    error.set(true);
                                }
                            }
                        }));

        // Thread 2: Seek
        futures.add(
                executor.submit(
                        () -> {
                            try {
                                barrier.await();
                                for (int i = 0; i < 20; i++) {
                                    engine.seek(playback, i * 1000);
                                    Thread.sleep(20);
                                }
                            } catch (Exception e) {
                                if (!(e instanceof AudioPlaybackException)) {
                                    error.set(true);
                                }
                            }
                        }));

        // Thread 3: Get position
        futures.add(
                executor.submit(
                        () -> {
                            try {
                                barrier.await();
                                for (int i = 0; i < 40; i++) {
                                    engine.getPosition(playback);
                                    engine.getState(playback);
                                    Thread.sleep(10);
                                }
                            } catch (Exception e) {
                                error.set(true);
                            }
                        }));

        // Thread 4: Add/remove listeners
        futures.add(
                executor.submit(
                        () -> {
                            try {
                                barrier.await();
                                TestListener listener = new TestListener();
                                for (int i = 0; i < 20; i++) {
                                    engine.addPlaybackListener(listener);
                                    Thread.sleep(10);
                                    engine.removePlaybackListener(listener);
                                    Thread.sleep(10);
                                }
                            } catch (Exception e) {
                                error.set(true);
                            }
                        }));

        // Wait for completion
        for (Future<?> future : futures) {
            future.get(3, TimeUnit.SECONDS);
        }

        assertFalse(error.get(), "No unexpected errors should occur");

        // Engine should still be functional
        engine.stop(playback);
        PlaybackHandle newPlayback = engine.play(handle);
        assertTrue(engine.isPlaying(newPlayback));
        engine.stop(newPlayback);

        executor.shutdown();
    }

    // ========== play() with range Tests ==========

    @Test
    @DisplayName("Should test play() with range method behavior")
    void testPlayRangeMethod() throws Exception {
        AudioHandle handle = engine.loadAudio(SAMPLE_WAV);
        AudioMetadata metadata = engine.getMetadata(handle);

        TestListener listener = new TestListener();
        engine.addPlaybackListener(listener);

        long startFrame = metadata.frameCount() / 4;
        long endFrame = metadata.frameCount() / 2;

        PlaybackHandle playback = engine.play(handle, startFrame, endFrame);
        assertNotNull(playback);

        Thread.sleep(100);
        assertTrue(!listener.stateChanges.isEmpty(), "No state changes received");
        assertTrue(
                listener.stateChanges.contains(PlaybackState.PLAYING),
                "Expected PLAYING state, got: " + listener.stateChanges);

        engine.stop(playback);
        assertTrue(listener.waitForStateChange(PlaybackState.STOPPED));
    }

    @Test
    @DisplayName("Should validate play() range parameters")
    void testPlayRangeValidation() throws Exception {
        AudioHandle handle = engine.loadAudio(SAMPLE_WAV);
        AudioMetadata metadata = engine.getMetadata(handle);

        assertThrows(AudioPlaybackException.class, () -> engine.play(handle, -1, 1000));
        assertThrows(AudioPlaybackException.class, () -> engine.play(handle, 1000, 500));

        PlaybackHandle playback = engine.play(handle, 0, metadata.frameCount() / 2);
        assertNotNull(playback);
        engine.stop(playback);
    }

    // ========== State Consistency Tests ==========

    @Test
    @DisplayName("Should maintain state consistency across operations")
    void testStateConsistency() throws Exception {
        TestListener listener = new TestListener();
        engine.addPlaybackListener(listener);

        AudioHandle handle = engine.loadAudio(SAMPLE_WAV);

        // Initial state
        assertEquals(FmodSystemStateManager.State.INITIALIZED, stateManager.getCurrentState());

        // Start playback
        PlaybackHandle playback = engine.play(handle);
        assertTrue(((FmodPlaybackHandle) playback).isActive());

        // Pause should maintain handle validity
        engine.pause(playback);
        assertTrue(((FmodPlaybackHandle) playback).isActive());

        // Stop should invalidate handle
        engine.stop(playback);
        assertFalse(((FmodPlaybackHandle) playback).isActive());

        // New playback should get new handle
        PlaybackHandle playback2 = engine.play(handle);
        assertNotSame(playback, playback2);
        assertTrue(((FmodPlaybackHandle) playback2).isActive());
        assertFalse(((FmodPlaybackHandle) playback).isActive());

        engine.stop(playback2);
    }

    @Test
    @DisplayName("Should handle rapid state transitions")
    void testRapidStateTransitions() throws Exception {
        AudioHandle handle = engine.loadAudio(SAMPLE_WAV);

        for (int i = 0; i < 10; i++) {
            PlaybackHandle playback = engine.play(handle);
            engine.pause(playback);
            engine.resume(playback);
            engine.pause(playback);
            engine.stop(playback);
        }

        // Engine should still be functional
        PlaybackHandle finalPlayback = engine.play(handle);
        assertTrue(engine.isPlaying(finalPlayback));
        engine.stop(finalPlayback);
    }

    // ========== Listener Tests ==========

    @Test
    @DisplayName("Should notify multiple listeners correctly")
    void testMultipleListeners() throws Exception {
        TestListener listener1 = new TestListener();
        TestListener listener2 = new TestListener();
        TestListener listener3 = new TestListener();

        engine.addPlaybackListener(listener1);
        engine.addPlaybackListener(listener2);
        engine.addPlaybackListener(listener3);

        AudioHandle handle = engine.loadAudio(SAMPLE_WAV);
        PlaybackHandle playback = engine.play(handle);

        // All listeners should receive state change (synchronous notification)
        Thread.sleep(50); // Give a moment for any async processing
        assertTrue(listener1.stateChanges.contains(PlaybackState.PLAYING));
        assertTrue(listener2.stateChanges.contains(PlaybackState.PLAYING));
        assertTrue(listener3.stateChanges.contains(PlaybackState.PLAYING));

        // Remove one listener
        engine.removePlaybackListener(listener2);

        engine.pause(playback);

        // Only remaining listeners should receive pause
        Thread.sleep(50); // Give a moment for any async processing
        assertTrue(listener1.stateChanges.contains(PlaybackState.PAUSED));
        assertTrue(listener3.stateChanges.contains(PlaybackState.PAUSED));
        assertFalse(listener2.stateChanges.contains(PlaybackState.PAUSED));

        engine.stop(playback);
    }

    // ========== Handle Invalidation Bug Tests ==========

    @Test
    @DisplayName("Loading new audio should invalidate previous handle")
    void testPreviousHandleInvalidatedOnNewLoad() throws Exception {
        // Load first audio file
        AudioHandle firstHandle = engine.loadAudio(SAMPLE_WAV);
        assertNotNull(firstHandle);
        assertTrue(
                ((FmodAudioHandle) firstHandle).isValid(),
                "First handle should be valid after loading");

        // Verify we can play the first handle
        PlaybackHandle playback1 = engine.play(firstHandle);
        assertNotNull(playback1);
        engine.stop(playback1);

        // Load second audio file - this should invalidate the first handle
        AudioHandle secondHandle = engine.loadAudio(SWEEP_WAV);
        assertNotNull(secondHandle);
        assertTrue(
                ((FmodAudioHandle) secondHandle).isValid(),
                "Second handle should be valid after loading");

        // First handle should be invalid after loading new audio
        assertFalse(
                ((FmodAudioHandle) firstHandle).isValid(),
                "First handle should be INVALID after second audio is loaded");
    }

    @Test
    @DisplayName("Old handle should not be playable after new load")
    void testCannotPlayOldHandleAfterNewLoad() throws Exception {
        // Load first audio
        AudioHandle firstHandle = engine.loadAudio(SAMPLE_WAV);

        // Load second audio - should invalidate first
        engine.loadAudio(SWEEP_WAV);

        // Should throw exception when trying to play old handle
        assertThrows(
                Exception.class,
                () -> engine.play(firstHandle),
                "Playing old handle after new load should throw exception");
    }

    @Test
    @DisplayName("Old playback handle should become invalid on new load")
    void testPlaybackHandleInvalidatedOnNewLoad() throws Exception {
        // Load and start playing first audio
        AudioHandle firstHandle = engine.loadAudio(SAMPLE_WAV);
        PlaybackHandle playback = engine.play(firstHandle);
        assertTrue(engine.isPlaying(playback), "Should be playing initially");

        // Load new audio while first is playing
        engine.loadAudio(SWEEP_WAV);

        // Old playback should be stopped/invalid after loading new audio
        assertFalse(
                engine.isPlaying(playback), "Old playback should stop when new audio is loaded");
        assertTrue(engine.isStopped(playback), "Old playback should be in stopped state");
        assertFalse(
                ((FmodPlaybackHandle) playback).isActive(),
                "Old playback handle should be inactive");
    }

    @Test
    @DisplayName("Only the most recent handle should be valid")
    void testOnlyMostRecentHandleValid() throws Exception {
        // Load multiple files in sequence
        AudioHandle handle1 = engine.loadAudio(SAMPLE_WAV);
        AudioHandle handle2 = engine.loadAudio(SWEEP_WAV);
        AudioHandle handle3 = engine.loadAudio(SAMPLE_WAV); // Load first file again

        // Only the last handle should be valid
        assertFalse(((FmodAudioHandle) handle1).isValid(), "Handle 1 should be invalid");
        assertFalse(((FmodAudioHandle) handle2).isValid(), "Handle 2 should be invalid");
        assertTrue(((FmodAudioHandle) handle3).isValid(), "Handle 3 should be valid");

        // Only the last handle should be playable
        assertThrows(Exception.class, () -> engine.play(handle1));
        assertThrows(Exception.class, () -> engine.play(handle2));
        assertDoesNotThrow(
                () -> {
                    PlaybackHandle p = engine.play(handle3);
                    engine.stop(p);
                });
    }

    @Test
    @DisplayName("Handle validation should prevent operations on stale handles")
    void testHandleValidationPreventsStaleOperations() throws Exception {
        AudioHandle handle1 = engine.loadAudio(SAMPLE_WAV);

        // These operations should work
        assertDoesNotThrow(() -> engine.getMetadata(handle1));
        assertDoesNotThrow(
                () -> {
                    PlaybackHandle p = engine.play(handle1);
                    engine.stop(p);
                });

        // Load new file
        engine.loadAudio(SWEEP_WAV);

        // Now operations on handle1 should fail
        assertThrows(
                Exception.class,
                () -> engine.getMetadata(handle1),
                "Getting metadata for stale handle should throw");
        assertThrows(
                Exception.class, () -> engine.play(handle1), "Playing stale handle should throw");
    }

    // ========== State Consistency After Stop Tests ==========

    @Test
    @DisplayName("Should handle state check immediately after stop")
    @Timeout(3)
    void testStateCheckAfterStop() throws Exception {
        AudioHandle handle = engine.loadAudio(SAMPLE_WAV);

        for (int i = 0; i < 20; i++) {
            PlaybackHandle playback = engine.play(handle);

            // Stop releases the channel
            engine.stop(playback);

            // These calls immediately after stop should handle channel issues gracefully
            // Currently may throw "error code: 3"
            assertDoesNotThrow(() -> engine.getState(playback));
            assertDoesNotThrow(() -> engine.isPlaying(playback));
            assertDoesNotThrow(() -> engine.isPaused(playback));

            // Should report stopped state
            assertEquals(PlaybackState.STOPPED, engine.getState(playback));
            assertFalse(engine.isPlaying(playback));
            assertFalse(engine.isPaused(playback));
            assertTrue(engine.isStopped(playback));
        }
    }

    // Test listener implementation
    private static class TestListener implements PlaybackListener {
        final List<PlaybackState> stateChanges = Collections.synchronizedList(new ArrayList<>());
        final List<Long> progressPositions = Collections.synchronizedList(new ArrayList<>());
        final AtomicInteger progressCount = new AtomicInteger();
        final AtomicInteger playbackCompleteCount = new AtomicInteger();

        private final AtomicReference<CountDownLatch> stateLatch = new AtomicReference<>();
        private final AtomicReference<CountDownLatch> progressLatch = new AtomicReference<>();
        private final AtomicReference<PlaybackState> expectedState = new AtomicReference<>();

        @Override
        public void onProgress(PlaybackHandle handle, long positionFrames, long totalFrames) {
            progressCount.incrementAndGet();
            progressPositions.add(positionFrames);
            CountDownLatch latch = progressLatch.get();
            if (latch != null) {
                latch.countDown();
            }
        }

        @Override
        public void onStateChanged(
                PlaybackHandle handle, PlaybackState newState, PlaybackState oldState) {
            stateChanges.add(newState);
            CountDownLatch latch = stateLatch.get();
            PlaybackState expected = expectedState.get();
            if (latch != null && expected == newState) {
                latch.countDown();
            }
        }

        @Override
        public void onPlaybackComplete(PlaybackHandle handle) {
            playbackCompleteCount.incrementAndGet();
        }

        boolean waitForStateChange(PlaybackState state) throws InterruptedException {
            // If the state already arrived, no need to wait
            if (stateChanges.contains(state)) {
                return true;
            }

            CountDownLatch latch = new CountDownLatch(1);
            expectedState.set(state);
            stateLatch.set(latch);

            // Check again in case the event fired between set() and await()
            if (stateChanges.contains(state)) {
                return true;
            }

            return latch.await(2, TimeUnit.SECONDS);
        }

        boolean waitForProgress(int count, long timeout, TimeUnit unit)
                throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(count);
            progressLatch.set(latch);
            return latch.await(timeout, unit);
        }

        List<Long> getProgressPositions() {
            return new ArrayList<>(progressPositions);
        }
    }
}
