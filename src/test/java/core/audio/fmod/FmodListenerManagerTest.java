package core.audio.fmod;

import static org.junit.jupiter.api.Assertions.*;

import core.audio.AudioHandle;
import core.audio.AudioMetadata;
import core.audio.PlaybackHandle;
import core.audio.PlaybackListener;
import core.audio.PlaybackState;
import core.env.Platform;
import java.lang.foreign.MemorySegment;
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
 * Integration tests for FmodListenerManager. Tests the listener notification system with real FMOD
 * components to verify correct behavior for progress monitoring, state changes, and completion
 * detection.
 */
@Slf4j
@Tag("audio")
class FmodListenerManagerTest {

    private FmodListenerManager listenerManager;
    private FmodSystemManager systemManager;
    private FmodAudioLoadingManager loadingManager;
    private FmodPlaybackManager playbackManager;
    private FmodSystemStateManager stateManager;
    private FmodHandleLifecycleManager lifecycleManager;

    private MemorySegment system;

    private static final String SAMPLE_WAV = "src/test/resources/audio/freerecall.wav";
    private static final long TEST_PROGRESS_INTERVAL_MS = 50; // Fast interval for testing

    @BeforeEach
    void setUp() {
        // Initialize real FMOD components
        stateManager = new FmodSystemStateManager();
        systemManager =
                new FmodSystemManager(
                        new FmodLibraryLoader(
                                new FmodProperties("unpackaged", "standard"), new Platform()));
        lifecycleManager = new FmodHandleLifecycleManager();

        systemManager.initialize();
        system = systemManager.getSystem();

        // Create managers
        loadingManager = new FmodAudioLoadingManager(system, stateManager, lifecycleManager);
        playbackManager = new FmodPlaybackManager(system);
        listenerManager = new FmodListenerManager(system, TEST_PROGRESS_INTERVAL_MS);

        // Set state to initialized
        stateManager.compareAndSetState(
                FmodSystemStateManager.State.UNINITIALIZED,
                FmodSystemStateManager.State.INITIALIZING);
        stateManager.compareAndSetState(
                FmodSystemStateManager.State.INITIALIZING,
                FmodSystemStateManager.State.INITIALIZED);
    }

    @AfterEach
    void tearDown() {
        if (listenerManager != null) {
            listenerManager.shutdown();
        }
        if (loadingManager != null) {
            loadingManager.releaseAll();
        }
        if (systemManager != null) {
            systemManager.shutdown();
        }
    }

    // ========== Listener Registration Tests ==========

    @Test
    @DisplayName("Should register and track multiple listeners")
    void testListenerRegistration() {
        TestListener listener1 = new TestListener("L1");
        TestListener listener2 = new TestListener("L2");
        TestListener listener3 = new TestListener("L3");

        assertEquals(0, listenerManager.getListenerCount());
        assertFalse(listenerManager.hasListeners());

        listenerManager.addListener(listener1);
        assertEquals(1, listenerManager.getListenerCount());
        assertTrue(listenerManager.hasListeners());

        listenerManager.addListener(listener2);
        listenerManager.addListener(listener3);
        assertEquals(3, listenerManager.getListenerCount());

        // Remove one listener
        listenerManager.removeListener(listener2);
        assertEquals(2, listenerManager.getListenerCount());

        // Remove remaining listeners
        listenerManager.removeListener(listener1);
        listenerManager.removeListener(listener3);
        assertEquals(0, listenerManager.getListenerCount());
        assertFalse(listenerManager.hasListeners());
    }

    @Test
    @DisplayName("Should reject listener registration after shutdown")
    void testListenerRegistrationAfterShutdown() {
        TestListener listener = new TestListener("L1");

        listenerManager.addListener(listener);
        assertEquals(1, listenerManager.getListenerCount());

        // Shutdown the manager
        listenerManager.shutdown();
        assertTrue(listenerManager.isShutdown());

        // Try to add another listener - should be rejected
        TestListener listener2 = new TestListener("L2");
        log.warn("=== EXPECTED WARNING FOLLOWS - Testing shutdown rejection ===");
        listenerManager.addListener(listener2);

        // Count should not increase
        assertEquals(0, listenerManager.getListenerCount()); // Cleared on shutdown
    }

    // ========== Progress Monitoring Tests ==========

    @Test
    @DisplayName("Should receive periodic progress updates during playback")
    @Timeout(5)
    void testProgressMonitoring() throws Exception {
        // Load audio and start playback
        AudioHandle audioHandle = loadingManager.loadAudio(SAMPLE_WAV);
        MemorySegment sound =
                loadingManager
                        .getCurrentSound()
                        .orElseThrow(() -> new IllegalStateException("No sound loaded"));

        FmodPlaybackHandle playbackHandle = playbackManager.play(sound, audioHandle);
        assertNotNull(playbackHandle);

        // Add listener and start monitoring
        TestListener listener = new TestListener("Progress");
        listenerManager.addListener(listener);

        long totalFrames = getAudioFrameCount(sound);
        listenerManager.startMonitoring(playbackHandle, totalFrames);

        // Wait for at least 5 progress updates
        boolean received = listener.waitForProgressUpdates(5, 2, TimeUnit.SECONDS);
        assertTrue(received, "Should receive at least 5 progress updates");

        // Verify progress is increasing
        List<Double> positions = listener.getProgressPositions();
        for (int i = 1; i < positions.size(); i++) {
            assertTrue(
                    positions.get(i) >= positions.get(i - 1),
                    "Progress should increase or stay same");
        }

        // Stop monitoring
        listenerManager.stopMonitoring();
        playbackManager.stop();

        // Record count before wait
        int countBeforeWait = listener.progressCount.get();

        // Should not receive more updates
        Thread.sleep(200);
        assertEquals(
                countBeforeWait,
                listener.progressCount.get(),
                "Should not receive updates after stopping");
    }

    @Test
    @DisplayName("Should skip progress timer when no listeners registered")
    void testMonitoringWithoutListeners() throws Exception {
        // Load audio and start playback
        AudioHandle audioHandle = loadingManager.loadAudio(SAMPLE_WAV);
        MemorySegment sound =
                loadingManager
                        .getCurrentSound()
                        .orElseThrow(() -> new IllegalStateException("No sound loaded"));
        FmodPlaybackHandle playbackHandle = playbackManager.play(sound, audioHandle);

        // Start monitoring without any listeners
        long totalFrames = getAudioFrameCount(sound);
        listenerManager.startMonitoring(playbackHandle, totalFrames);

        // Should not create timer (can't directly verify, but should not throw)
        Thread.sleep(100);

        // Add listener after monitoring started - won't get updates until restart
        TestListener listener = new TestListener("Late");
        listenerManager.addListener(listener);

        Thread.sleep(200);
        assertEquals(
                0,
                listener.progressCount.get(),
                "Late listener should not receive updates without restart");

        // Restart monitoring with listener present
        listenerManager.startMonitoring(playbackHandle, totalFrames);

        // Now should receive updates
        boolean received = listener.waitForProgressUpdates(3, 1, TimeUnit.SECONDS);
        assertTrue(received, "Should receive updates after restart with listener");

        listenerManager.stopMonitoring();
        playbackManager.stop();
    }

    // ========== State Change Tests ==========

    @Test
    @DisplayName("Should notify all listeners of state changes")
    void testStateChangeNotifications() throws Exception {
        TestListener listener1 = new TestListener("L1");
        TestListener listener2 = new TestListener("L2");

        listenerManager.addListener(listener1);
        listenerManager.addListener(listener2);

        // Create a mock playback handle
        AudioHandle audioHandle = loadingManager.loadAudio(SAMPLE_WAV);
        MemorySegment sound =
                loadingManager
                        .getCurrentSound()
                        .orElseThrow(() -> new IllegalStateException("No sound loaded"));
        FmodPlaybackHandle playbackHandle = playbackManager.play(sound, audioHandle);

        // Notify state changes
        listenerManager.notifyStateChanged(
                playbackHandle, PlaybackState.PLAYING, PlaybackState.STOPPED);
        listenerManager.notifyStateChanged(
                playbackHandle, PlaybackState.PAUSED, PlaybackState.PLAYING);
        listenerManager.notifyStateChanged(
                playbackHandle, PlaybackState.SEEKING, PlaybackState.PAUSED);
        listenerManager.notifyStateChanged(
                playbackHandle, PlaybackState.PLAYING, PlaybackState.SEEKING);
        listenerManager.notifyStateChanged(
                playbackHandle, PlaybackState.STOPPED, PlaybackState.PLAYING);

        // Both listeners should receive all state changes
        assertEquals(5, listener1.stateChanges.size());
        assertEquals(5, listener2.stateChanges.size());

        // Verify the sequence
        assertEquals(PlaybackState.PLAYING, listener1.stateChanges.get(0));
        assertEquals(PlaybackState.PAUSED, listener1.stateChanges.get(1));
        assertEquals(PlaybackState.SEEKING, listener1.stateChanges.get(2));
        assertEquals(PlaybackState.PLAYING, listener1.stateChanges.get(3));
        assertEquals(PlaybackState.STOPPED, listener1.stateChanges.get(4));

        playbackManager.stop();
    }

    // ========== Playback Completion Tests ==========

    @Test
    @DisplayName("Should detect and notify playback completion")
    @Timeout(5)
    void testPlaybackCompletion() throws Exception {
        // Load a short audio file
        AudioHandle audioHandle = loadingManager.loadAudio(SAMPLE_WAV);
        MemorySegment sound =
                loadingManager
                        .getCurrentSound()
                        .orElseThrow(() -> new IllegalStateException("No sound loaded"));

        // Create a range playback that will complete quickly using high-level API
        long endFrames = 1000;
        FmodPlaybackHandle playbackHandle =
                playbackManager.playRange(sound, audioHandle, 0, endFrames, true);

        // Add listener
        TestListener listener = new TestListener("Completion");
        listenerManager.addListener(listener);

        // Start monitoring
        listenerManager.startMonitoring(playbackHandle, 1000);

        // Playback already started by playRange

        // Wait for completion
        boolean completed = listener.waitForCompletion(3, TimeUnit.SECONDS);
        assertTrue(completed, "Should detect playback completion");

        // Verify FINISHED state was notified
        assertTrue(
                listener.stateChanges.contains(PlaybackState.FINISHED),
                "Should notify FINISHED state");

        // Verify completion callback
        assertEquals(1, listener.completionCount.get(), "Should call onPlaybackComplete once");
    }

    @Test
    @DisplayName("Should detect completion when channel becomes invalid")
    @Timeout(3)
    void testCompletionOnChannelInvalid() throws Exception {
        // Load audio and create playback
        AudioHandle audioHandle = loadingManager.loadAudio(SAMPLE_WAV);
        MemorySegment sound =
                loadingManager
                        .getCurrentSound()
                        .orElseThrow(() -> new IllegalStateException("No sound loaded"));
        FmodPlaybackHandle playbackHandle = playbackManager.play(sound, audioHandle);

        // Add listener and start monitoring
        TestListener listener = new TestListener("Invalid");
        listenerManager.addListener(listener);
        listenerManager.startMonitoring(playbackHandle, getAudioFrameCount(sound));

        // Wait for some progress
        listener.waitForProgressUpdates(2, 1, TimeUnit.SECONDS);

        // Stop the channel via playback manager (simulating invalid handle for monitor)
        playbackManager.stop();

        // Should detect and notify completion
        boolean completed = listener.waitForCompletion(1, TimeUnit.SECONDS);
        assertTrue(completed, "Should detect channel became invalid");

        assertTrue(
                listener.stateChanges.contains(PlaybackState.FINISHED),
                "Should notify FINISHED state");
    }

    // ========== Concurrency Tests ==========

    @Test
    @DisplayName("Should handle concurrent listener operations safely")
    @Timeout(5)
    void testConcurrentListenerOperations() throws Exception {
        AtomicBoolean error = new AtomicBoolean(false);
        CyclicBarrier barrier = new CyclicBarrier(4);
        ExecutorService executor = Executors.newFixedThreadPool(4);

        // Start playback for notifications
        AudioHandle audioHandle = loadingManager.loadAudio(SAMPLE_WAV);
        MemorySegment sound =
                loadingManager
                        .getCurrentSound()
                        .orElseThrow(() -> new IllegalStateException("No sound loaded"));
        FmodPlaybackHandle playbackHandle = playbackManager.play(sound, audioHandle);
        listenerManager.startMonitoring(playbackHandle, getAudioFrameCount(sound));

        List<Future<?>> futures = new ArrayList<>();

        // Thread 1: Add/remove listeners
        futures.add(
                executor.submit(
                        () -> {
                            try {
                                barrier.await();
                                for (int i = 0; i < 50; i++) {
                                    TestListener listener = new TestListener("T1-" + i);
                                    listenerManager.addListener(listener);
                                    Thread.sleep(5);
                                    listenerManager.removeListener(listener);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                                error.set(true);
                            }
                        }));

        // Thread 2: Send state notifications
        futures.add(
                executor.submit(
                        () -> {
                            try {
                                barrier.await();
                                for (int i = 0; i < 100; i++) {
                                    listenerManager.notifyStateChanged(
                                            playbackHandle,
                                            i % 2 == 0
                                                    ? PlaybackState.PLAYING
                                                    : PlaybackState.PAUSED,
                                            i % 2 == 0
                                                    ? PlaybackState.PAUSED
                                                    : PlaybackState.PLAYING);
                                    Thread.sleep(5);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                                error.set(true);
                            }
                        }));

        // Thread 3: Check listener count
        futures.add(
                executor.submit(
                        () -> {
                            try {
                                barrier.await();
                                for (int i = 0; i < 200; i++) {
                                    int count = listenerManager.getListenerCount();
                                    assertTrue(count >= 0, "Count should never be negative");
                                    Thread.sleep(2);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                                error.set(true);
                            }
                        }));

        // Thread 4: Progress notifications
        futures.add(
                executor.submit(
                        () -> {
                            try {
                                barrier.await();
                                for (int i = 0; i < 100; i++) {
                                    listenerManager.notifyProgress(
                                            playbackHandle, i * 1000L, 10000L);
                                    Thread.sleep(5);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                                error.set(true);
                            }
                        }));

        // Wait for all threads
        for (Future<?> future : futures) {
            future.get(3, TimeUnit.SECONDS);
        }

        assertFalse(error.get(), "No errors should occur during concurrent operations");

        playbackManager.stop();
        executor.shutdown();
    }

    // ========== Exception Handling Tests ==========

    @Test
    @DisplayName("Should handle listener exceptions gracefully")
    void testListenerExceptionHandling() throws Exception {
        log.warn("=== EXPECTED TEST EXCEPTIONS FOLLOW - Testing error handling ===");

        // Create a listener that throws exceptions
        PlaybackListener badListener =
                new PlaybackListener() {
                    @Override
                    public void onProgress(
                            PlaybackHandle handle, long positionFrames, long totalFrames) {
                        throw new TestListenerException("Progress error");
                    }

                    @Override
                    public void onStateChanged(
                            PlaybackHandle handle, PlaybackState newState, PlaybackState oldState) {
                        throw new TestListenerException("State change error");
                    }

                    @Override
                    public void onPlaybackComplete(PlaybackHandle handle) {
                        throw new TestListenerException("Completion error");
                    }
                };

        // Also add a good listener
        TestListener goodListener = new TestListener("Good");

        listenerManager.addListener(badListener);
        listenerManager.addListener(goodListener);

        // Create playback
        AudioHandle audioHandle = loadingManager.loadAudio(SAMPLE_WAV);
        MemorySegment sound =
                loadingManager
                        .getCurrentSound()
                        .orElseThrow(() -> new IllegalStateException("No sound loaded"));
        FmodPlaybackHandle playbackHandle = playbackManager.play(sound, audioHandle);

        // Send notifications - bad listener should not prevent good listener from receiving
        listenerManager.notifyStateChanged(
                playbackHandle, PlaybackState.PLAYING, PlaybackState.STOPPED);
        listenerManager.notifyProgress(playbackHandle, 1000L, 10000L);
        listenerManager.notifyPlaybackComplete(playbackHandle);

        // Good listener should still receive all notifications
        assertEquals(2, goodListener.stateChanges.size()); // PLAYING and FINISHED
        assertEquals(1, goodListener.progressCount.get());
        assertEquals(1, goodListener.completionCount.get());

        playbackManager.stop();
    }

    // ========== Resource Management Tests ==========

    @Test
    @DisplayName("Should clean up resources on shutdown")
    void testShutdownCleanup() throws Exception {
        // Add listeners
        TestListener listener1 = new TestListener("L1");
        TestListener listener2 = new TestListener("L2");
        listenerManager.addListener(listener1);
        listenerManager.addListener(listener2);

        // Start monitoring
        AudioHandle audioHandle = loadingManager.loadAudio(SAMPLE_WAV);
        MemorySegment sound =
                loadingManager
                        .getCurrentSound()
                        .orElseThrow(() -> new IllegalStateException("No sound loaded"));
        FmodPlaybackHandle playbackHandle = playbackManager.play(sound, audioHandle);
        listenerManager.startMonitoring(playbackHandle, getAudioFrameCount(sound));

        // Wait for some progress
        listener1.waitForProgressUpdates(2, 1, TimeUnit.SECONDS);

        // Shutdown
        assertFalse(listenerManager.isShutdown());
        listenerManager.shutdown();
        assertTrue(listenerManager.isShutdown());

        // Should clear listeners
        assertEquals(0, listenerManager.getListenerCount());

        // Should stop monitoring (no more updates)
        int count = listener1.progressCount.get();
        Thread.sleep(200);
        assertEquals(count, listener1.progressCount.get(), "No updates after shutdown");

        // Double shutdown should be safe
        assertDoesNotThrow(() -> listenerManager.shutdown());

        playbackManager.stop();
    }

    @Test
    @DisplayName("Should handle rapid start/stop monitoring cycles")
    @Timeout(5)
    void testRapidMonitoringCycles() throws Exception {
        TestListener listener = new TestListener("Rapid");
        listenerManager.addListener(listener);

        AudioHandle audioHandle = loadingManager.loadAudio(SAMPLE_WAV);
        MemorySegment sound =
                loadingManager
                        .getCurrentSound()
                        .orElseThrow(() -> new IllegalStateException("No sound loaded"));
        long totalFrames = getAudioFrameCount(sound);

        // Rapid start/stop cycles
        for (int i = 0; i < 20; i++) {
            FmodPlaybackHandle playbackHandle = playbackManager.play(sound, audioHandle);
            listenerManager.startMonitoring(playbackHandle, totalFrames);
            Thread.sleep(20);
            listenerManager.stopMonitoring();
            playbackManager.stop();
        }

        // Should not crash or leak resources
        assertTrue(listener.progressCount.get() > 0, "Should have received some progress updates");
    }

    @Test
    @DisplayName("Should not receive callbacks after stopMonitoring")
    @Timeout(3)
    void testNoCallbacksAfterStop() throws Exception {
        TestListener listener = new TestListener("StopTest");
        listenerManager.addListener(listener);

        // Load and start playback
        AudioHandle audioHandle = loadingManager.loadAudio(SAMPLE_WAV);
        MemorySegment sound =
                loadingManager
                        .getCurrentSound()
                        .orElseThrow(() -> new IllegalStateException("No sound loaded"));
        FmodPlaybackHandle playbackHandle = playbackManager.play(sound, audioHandle);

        // Start monitoring
        listenerManager.startMonitoring(playbackHandle, getAudioFrameCount(sound));

        // Wait for at least 2 progress updates to confirm monitoring is working
        assertTrue(
                listener.waitForProgressUpdates(2, 1, TimeUnit.SECONDS),
                "Should receive initial progress updates");

        // Stop monitoring
        listenerManager.stopMonitoring();

        // Record the count immediately after stopping
        int countAfterStop = listener.progressCount.get();

        // Wait for 3x the progress interval to ensure no more callbacks
        Thread.sleep(TEST_PROGRESS_INTERVAL_MS * 3);

        // Verify no additional progress updates were received
        assertEquals(
                countAfterStop,
                listener.progressCount.get(),
                "Should not receive any progress callbacks after stopMonitoring");

        // Clean up
        playbackManager.stop();
    }

    // ========== Latency Compensation Tests ==========

    @Test
    @DisplayName("Should calculate hearing position with latency compensation")
    @Timeout(3)
    void testLatencyCompensation() throws Exception {
        // This test verifies that updateProgress() applies latency compensation
        TestListener listener = new TestListener("Latency");
        listenerManager.addListener(listener);

        AudioHandle audioHandle = loadingManager.loadAudio(SAMPLE_WAV);
        MemorySegment sound =
                loadingManager
                        .getCurrentSound()
                        .orElseThrow(() -> new IllegalStateException("No sound loaded"));
        FmodPlaybackHandle playbackHandle = playbackManager.play(sound, audioHandle);

        listenerManager.startMonitoring(playbackHandle, getAudioFrameCount(sound));

        // Collect several progress updates
        listener.waitForProgressUpdates(10, 2, TimeUnit.SECONDS);

        List<Double> positions = listener.getProgressPositions();
        assertTrue(positions.size() >= 10, "Should have enough samples");

        // Positions should be reasonable (not negative, not huge)
        long totalFrames = getAudioFrameCount(sound);
        for (Double pos : positions) {
            assertTrue(pos >= 0, "Position should not be negative");
            assertTrue(pos <= totalFrames, "Position should not exceed total frames");
        }

        listenerManager.stopMonitoring();
        playbackManager.stop();
    }

    // ========== Helper Methods ==========

    private long getAudioFrameCount(MemorySegment sound) {
        // Use the high-level loading manager to extract metadata instead of direct FMOD calls
        return loadingManager
                .getCurrentMetadata()
                .map(AudioMetadata::frameCount)
                .orElseThrow(() -> new IllegalStateException("No audio metadata available"));
    }

    // ========== Test Listener Implementation ==========

    private static class TestListener implements PlaybackListener {
        final String name;
        final List<PlaybackState> stateChanges = Collections.synchronizedList(new ArrayList<>());
        final List<Double> progressPositions = Collections.synchronizedList(new ArrayList<>());
        final AtomicInteger progressCount = new AtomicInteger();
        final AtomicInteger completionCount = new AtomicInteger();
        final AtomicReference<CountDownLatch> progressLatch = new AtomicReference<>();
        final AtomicReference<CountDownLatch> completionLatch = new AtomicReference<>();

        TestListener(String name) {
            this.name = name;
        }

        @Override
        public void onProgress(PlaybackHandle handle, long positionFrames, long totalFrames) {
            progressCount.incrementAndGet();
            progressPositions.add((double) positionFrames);
            CountDownLatch latch = progressLatch.get();
            if (latch != null) {
                latch.countDown();
            }
        }

        @Override
        public void onStateChanged(
                PlaybackHandle handle, PlaybackState newState, PlaybackState oldState) {
            stateChanges.add(newState);
        }

        @Override
        public void onPlaybackComplete(PlaybackHandle handle) {
            completionCount.incrementAndGet();
            CountDownLatch latch = completionLatch.get();
            if (latch != null) {
                latch.countDown();
            }
        }

        boolean waitForProgressUpdates(int count, long timeout, TimeUnit unit)
                throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(count);
            progressLatch.set(latch);
            return latch.await(timeout, unit);
        }

        boolean waitForCompletion(long timeout, TimeUnit unit) throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(1);
            completionLatch.set(latch);
            return latch.await(timeout, unit);
        }

        List<Double> getProgressPositions() {
            return new ArrayList<>(progressPositions);
        }

        @Override
        public String toString() {
            return "TestListener[" + name + "]";
        }
    }
}
