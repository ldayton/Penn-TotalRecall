package a2.fmod;

import static org.junit.jupiter.api.Assertions.*;

import a2.PlaybackHandle;
import a2.PlaybackListener;
import a2.PlaybackState;
import annotations.AudioEngine;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;

/**
 * Integration tests for FmodListenerManager. Tests listener notifications and progress monitoring
 * with real FMOD operations.
 */
@AudioEngine
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FmodListenerManagerTest {

    private FmodLibrary fmod;
    private Pointer system;
    private FmodListenerManager listenerManager;

    // For creating test playback
    private FmodSystemStateManager stateManager;
    private FmodAudioLoadingManager loadingManager;
    private FmodPlaybackManager playbackManager;

    // Test audio file
    private static final String SAMPLE_WAV = "packaging/samples/sample.wav";
    private static final String SWEEP_WAV = "packaging/samples/sweep.wav";
    private static final int PROGRESS_INTERVAL_MS = 20; // Fast interval for tests

    @BeforeAll
    void setUpFmod() {
        // Load FMOD library
        String resourcePath = getClass().getResource("/fmod/macos").getPath();
        NativeLibrary.addSearchPath("fmod", resourcePath);
        fmod = Native.load("fmod", FmodLibrary.class);

        // Create FMOD system
        PointerByReference systemRef = new PointerByReference();
        int result = fmod.FMOD_System_Create(systemRef, FmodConstants.FMOD_VERSION);
        assertEquals(FmodConstants.FMOD_OK, result, "Failed to create FMOD system");
        system = systemRef.getValue();

        // Initialize FMOD system
        result = fmod.FMOD_System_Init(system, 32, FmodConstants.FMOD_INIT_NORMAL, null);
        assertEquals(FmodConstants.FMOD_OK, result, "Failed to initialize FMOD system");

        // Create state manager for loading manager
        stateManager = new FmodSystemStateManager();
        assertTrue(
                stateManager.compareAndSetState(
                        FmodSystemStateManager.State.UNINITIALIZED,
                        FmodSystemStateManager.State.INITIALIZING));
        assertTrue(
                stateManager.compareAndSetState(
                        FmodSystemStateManager.State.INITIALIZING,
                        FmodSystemStateManager.State.INITIALIZED));
    }

    @BeforeEach
    void createManagers() {
        listenerManager = new FmodListenerManager(fmod, system, PROGRESS_INTERVAL_MS);
        loadingManager = new FmodAudioLoadingManager(fmod, system, stateManager);
        playbackManager = new FmodPlaybackManager(fmod, system);
    }

    @AfterAll
    void tearDownFmod() {
        if (listenerManager != null) {
            listenerManager.shutdown();
        }
        if (loadingManager != null) {
            loadingManager.releaseAll();
        }
        if (system != null && fmod != null) {
            fmod.FMOD_System_Release(system);
        }
    }

    // Test listener that captures all notifications
    static class TestListener implements PlaybackListener {
        final List<StateChange> stateChanges = Collections.synchronizedList(new ArrayList<>());
        final List<Progress> progressUpdates = Collections.synchronizedList(new ArrayList<>());
        final AtomicInteger completeCount = new AtomicInteger();
        final AtomicReference<CountDownLatch> progressLatch = new AtomicReference<>();
        final AtomicReference<CountDownLatch> completeLatch = new AtomicReference<>();

        static class StateChange {
            final PlaybackState newState;
            final PlaybackState oldState;

            StateChange(PlaybackState newState, PlaybackState oldState) {
                this.newState = newState;
                this.oldState = oldState;
            }
        }

        static class Progress {
            final long position;
            final long total;

            Progress(long position, long total) {
                this.position = position;
                this.total = total;
            }
        }

        @Override
        public void onProgress(PlaybackHandle handle, long positionFrames, long totalFrames) {
            progressUpdates.add(new Progress(positionFrames, totalFrames));
            CountDownLatch latch = progressLatch.get();
            if (latch != null) {
                latch.countDown();
            }
        }

        @Override
        public void onStateChanged(
                PlaybackHandle handle, PlaybackState newState, PlaybackState oldState) {
            stateChanges.add(new StateChange(newState, oldState));
        }

        @Override
        public void onPlaybackComplete(PlaybackHandle handle) {
            completeCount.incrementAndGet();
            CountDownLatch latch = completeLatch.get();
            if (latch != null) {
                latch.countDown();
            }
        }

        void expectProgress(int count) {
            progressLatch.set(new CountDownLatch(count));
        }

        void expectCompletion() {
            completeLatch.set(new CountDownLatch(1));
        }

        boolean waitForProgress(int count, long timeout, TimeUnit unit)
                throws InterruptedException {
            expectProgress(count);
            return progressLatch.get().await(timeout, unit);
        }

        boolean waitForCompletion(long timeout, TimeUnit unit) throws InterruptedException {
            expectCompletion();
            return completeLatch.get().await(timeout, unit);
        }
    }

    // ========== Listener Management Tests ==========

    @Test
    void testListenerAddRemove() {
        TestListener listener1 = new TestListener();
        TestListener listener2 = new TestListener();

        assertEquals(0, listenerManager.getListenerCount());

        listenerManager.addListener(listener1);
        assertEquals(1, listenerManager.getListenerCount());
        assertTrue(listenerManager.hasListeners());

        listenerManager.addListener(listener2);
        assertEquals(2, listenerManager.getListenerCount());

        listenerManager.removeListener(listener1);
        assertEquals(1, listenerManager.getListenerCount());
        assertTrue(listenerManager.hasListeners());

        listenerManager.removeListener(listener2);
        assertEquals(0, listenerManager.getListenerCount());
        assertFalse(listenerManager.hasListeners());
    }

    @Test
    void testListenerNotificationsReachAll() {
        TestListener listener1 = new TestListener();
        TestListener listener2 = new TestListener();
        TestListener listener3 = new TestListener();

        listenerManager.addListener(listener1);
        listenerManager.addListener(listener2);
        listenerManager.addListener(listener3);

        // Create a mock handle for testing
        Pointer mockSound = new Memory(8);
        FmodAudioHandle mockAudioHandle = new FmodAudioHandle(1, mockSound, "test.wav");
        Pointer mockChannel = new Memory(8);
        FmodPlaybackHandle mockHandle =
                new FmodPlaybackHandle(mockAudioHandle, mockChannel, 0, 100);

        // Send state change
        listenerManager.notifyStateChanged(
                mockHandle, PlaybackState.PLAYING, PlaybackState.STOPPED);

        // All listeners should receive it
        assertEquals(1, listener1.stateChanges.size());
        assertEquals(1, listener2.stateChanges.size());
        assertEquals(1, listener3.stateChanges.size());

        assertEquals(PlaybackState.PLAYING, listener1.stateChanges.get(0).newState);
        assertEquals(PlaybackState.STOPPED, listener1.stateChanges.get(0).oldState);
    }

    // ========== Monitoring Lifecycle Tests ==========

    @Test
    @Timeout(5)
    void testStartStopMonitoring() throws Exception {
        TestListener listener = new TestListener();
        listenerManager.addListener(listener);

        // Load and play audio
        loadingManager.loadAudio(SAMPLE_WAV);
        Pointer sound =
                loadingManager
                        .getCurrentSound()
                        .orElseThrow(() -> new IllegalStateException("Sound not loaded"));
        FmodPlaybackHandle handle =
                playbackManager.play(sound, loadingManager.getCurrentHandle().get());

        // Start monitoring
        listenerManager.startMonitoring(handle, 1000000); // Some large frame count

        // Should receive progress updates
        assertTrue(listener.waitForProgress(3, 1, TimeUnit.SECONDS));
        assertTrue(listener.progressUpdates.size() >= 3);

        // Stop monitoring
        listenerManager.stopMonitoring();

        // Clear existing updates and wait a bit
        listener.progressUpdates.clear();
        Thread.sleep(PROGRESS_INTERVAL_MS * 3);

        // Should not receive more updates
        assertEquals(0, listener.progressUpdates.size());

        playbackManager.stop();
    }

    @Test
    @Timeout(5)
    void testMonitoringSwitchover() throws Exception {
        TestListener listener = new TestListener();
        listenerManager.addListener(listener);

        // Load and play first audio
        loadingManager.loadAudio(SAMPLE_WAV);
        Pointer sound =
                loadingManager
                        .getCurrentSound()
                        .orElseThrow(() -> new IllegalStateException("Sound not loaded"));
        FmodPlaybackHandle handle1 =
                playbackManager.play(sound, loadingManager.getCurrentHandle().get());

        // Start monitoring first
        listenerManager.startMonitoring(handle1, 1000000);
        assertTrue(listener.waitForProgress(2, 1, TimeUnit.SECONDS));

        // Stop first playback, start second
        playbackManager.stop();
        FmodPlaybackHandle handle2 =
                playbackManager.play(sound, loadingManager.getCurrentHandle().get());

        // Switch monitoring to second
        listener.progressUpdates.clear();
        listenerManager.startMonitoring(handle2, 1000000);

        // Should receive progress for second handle
        assertTrue(listener.waitForProgress(2, 1, TimeUnit.SECONDS));
        assertTrue(listener.progressUpdates.size() >= 2);

        playbackManager.stop();
    }

    @Test
    @Timeout(5)
    void testMonitoringDetectsCompletion() throws Exception {
        TestListener listener = new TestListener();
        listenerManager.addListener(listener);

        // Load and play audio
        loadingManager.loadAudio(SAMPLE_WAV);
        Pointer sound =
                loadingManager
                        .getCurrentSound()
                        .orElseThrow(() -> new IllegalStateException("Sound not loaded"));
        FmodPlaybackHandle handle =
                playbackManager.play(sound, loadingManager.getCurrentHandle().get());

        // Start monitoring
        listenerManager.startMonitoring(handle, 1000000);

        // Should receive progress updates
        assertTrue(listener.waitForProgress(2, 1, TimeUnit.SECONDS));

        // Stop playback - this should trigger completion
        playbackManager.stop();

        // Should receive completion notification
        assertTrue(listener.waitForCompletion(1, TimeUnit.SECONDS));
        assertEquals(1, listener.completeCount.get());

        // Should have received FINISHED state change
        boolean foundFinished =
                listener.stateChanges.stream()
                        .anyMatch(sc -> sc.newState == PlaybackState.FINISHED);
        assertTrue(foundFinished, "Should receive FINISHED state");
    }

    // ========== Progress Update Tests ==========

    @Test
    @Timeout(5)
    void testProgressUpdatesDelivered() throws Exception {
        TestListener listener = new TestListener();
        listenerManager.addListener(listener);

        // Load and play audio
        loadingManager.loadAudio(SAMPLE_WAV);
        Pointer sound =
                loadingManager
                        .getCurrentSound()
                        .orElseThrow(() -> new IllegalStateException("Sound not loaded"));
        FmodPlaybackHandle handle =
                playbackManager.play(sound, loadingManager.getCurrentHandle().get());

        // Start monitoring
        long totalFrames = 1000000;
        listenerManager.startMonitoring(handle, totalFrames);

        // Collect progress updates
        assertTrue(listener.waitForProgress(5, 2, TimeUnit.SECONDS));

        // Verify progress data
        for (TestListener.Progress progress : listener.progressUpdates) {
            assertTrue(progress.position >= 0, "Position should be non-negative");
            assertEquals(totalFrames, progress.total, "Total frames should match");
        }

        // Progress should advance over time
        if (listener.progressUpdates.size() >= 2) {
            long firstPos = listener.progressUpdates.get(0).position;
            long lastPos =
                    listener.progressUpdates.get(listener.progressUpdates.size() - 1).position;
            assertTrue(lastPos > firstPos, "Position should advance");
        }

        playbackManager.stop();
    }

    @Test
    @Timeout(5)
    void testProgressDetectsEndFrame() throws Exception {
        TestListener listener = new TestListener();
        listenerManager.addListener(listener);

        // Load audio
        loadingManager.loadAudio(SAMPLE_WAV);
        Pointer sound =
                loadingManager
                        .getCurrentSound()
                        .orElseThrow(() -> new IllegalStateException("Sound not loaded"));

        // Play a short range
        long startFrame = 0;
        long endFrame = 4410; // 0.1 seconds at 44100 Hz
        FmodPlaybackHandle handle =
                playbackManager.playRange(
                        sound, loadingManager.getCurrentHandle().get(), startFrame, endFrame, true);

        // Start monitoring
        listenerManager.startMonitoring(handle, endFrame);

        // Should receive completion when reaching endFrame
        assertTrue(listener.waitForCompletion(2, TimeUnit.SECONDS));
        assertEquals(1, listener.completeCount.get());

        // Handle should be inactive
        assertFalse(handle.isActive());
    }

    @Test
    @Timeout(5)
    void testProgressContinuesOnListenerException() throws Exception {
        TestListener goodListener = new TestListener();
        PlaybackListener badListener =
                new PlaybackListener() {
                    @Override
                    public void onProgress(
                            PlaybackHandle handle, long positionFrames, long totalFrames) {
                        throw new RuntimeException("Test exception");
                    }

                    @Override
                    public void onStateChanged(
                            PlaybackHandle handle, PlaybackState newState, PlaybackState oldState) {
                        throw new RuntimeException("Test exception");
                    }

                    @Override
                    public void onPlaybackComplete(PlaybackHandle handle) {
                        throw new RuntimeException("Test exception");
                    }
                };

        listenerManager.addListener(badListener);
        listenerManager.addListener(goodListener);

        // Load and play audio
        loadingManager.loadAudio(SAMPLE_WAV);
        Pointer sound =
                loadingManager
                        .getCurrentSound()
                        .orElseThrow(() -> new IllegalStateException("Sound not loaded"));
        FmodPlaybackHandle handle =
                playbackManager.play(sound, loadingManager.getCurrentHandle().get());

        // Start monitoring
        listenerManager.startMonitoring(handle, 1000000);

        // Good listener should still receive updates despite bad listener throwing
        assertTrue(goodListener.waitForProgress(3, 1, TimeUnit.SECONDS));
        assertTrue(goodListener.progressUpdates.size() >= 3);

        playbackManager.stop();

        // Should still receive completion
        assertTrue(goodListener.waitForCompletion(1, TimeUnit.SECONDS));
    }

    // ========== State & Completion Tests ==========

    @Test
    void testProgressUpdateTimingAndAccuracy() throws Exception {
        // This test monitors progress over several seconds and verifies:
        // 1. Updates arrive at approximately the expected interval
        // 2. Reported positions are approximately correct

        // Create a new listener manager with 100ms interval for this test
        listenerManager.shutdown();
        listenerManager = new FmodListenerManager(fmod, system, 100);

        TestListener listener = new TestListener();
        listenerManager.addListener(listener);

        // Load audio file for testing
        loadingManager.loadAudio(SAMPLE_WAV);
        Pointer sound =
                loadingManager
                        .getCurrentSound()
                        .orElseThrow(() -> new IllegalStateException("Sound not loaded"));
        FmodAudioHandle audioHandle = loadingManager.getCurrentHandle().get();

        // Get actual duration
        IntByReference lengthRef = new IntByReference();
        fmod.FMOD_Sound_GetLength(sound, lengthRef, FmodConstants.FMOD_TIMEUNIT_MS);
        long durationMs = lengthRef.getValue();

        // This test uses 100ms interval
        long expectedIntervalMs = 100;

        // Track timing of updates
        List<Long> updateTimesMs = Collections.synchronizedList(new ArrayList<>());
        List<Long> reportedPositionsMs = Collections.synchronizedList(new ArrayList<>());
        long startTimeMs = System.currentTimeMillis();

        // Custom listener to track timing
        PlaybackListener timingListener =
                new PlaybackListener() {
                    @Override
                    public void onProgress(
                            PlaybackHandle handle, long positionFrames, long totalFrames) {
                        long currentTimeMs = System.currentTimeMillis();
                        updateTimesMs.add(currentTimeMs - startTimeMs);

                        // Convert frames to ms for comparison (assuming 44100 Hz)
                        long positionMs = (positionFrames * 1000) / 44100;
                        reportedPositionsMs.add(positionMs);
                    }

                    @Override
                    public void onStateChanged(
                            PlaybackHandle handle,
                            PlaybackState newState,
                            PlaybackState oldState) {}

                    @Override
                    public void onPlaybackComplete(PlaybackHandle handle) {}
                };

        listenerManager.addListener(timingListener);

        // Start playback
        FmodPlaybackHandle handle = playbackManager.play(sound, audioHandle);

        // Get total frames for monitoring
        fmod.FMOD_Sound_GetLength(sound, lengthRef, FmodConstants.FMOD_TIMEUNIT_PCM);
        long totalFrames = lengthRef.getValue();

        // Start monitoring
        listenerManager.startMonitoring(handle, totalFrames);

        // Let it play for 5 seconds (enough to get ~50 updates at 100ms interval)
        Thread.sleep(5000);

        // Stop playback
        playbackManager.stop();
        listenerManager.stopMonitoring();

        // Verify we got updates
        assertTrue(
                updateTimesMs.size() >= 40,
                "Should have at least 40 updates in 5 seconds, got " + updateTimesMs.size());

        // Verify timing intervals (allow 50ms tolerance due to thread scheduling)
        for (int i = 1; i < Math.min(updateTimesMs.size(), 20); i++) {
            long interval = updateTimesMs.get(i) - updateTimesMs.get(i - 1);
            assertTrue(
                    interval >= expectedIntervalMs - 50 && interval <= expectedIntervalMs + 50,
                    String.format(
                            "Update interval %d should be ~%dms, was %dms",
                            i, expectedIntervalMs, interval));
        }

        // Verify positions are increasing monotonically
        for (int i = 1; i < reportedPositionsMs.size(); i++) {
            assertTrue(
                    reportedPositionsMs.get(i) >= reportedPositionsMs.get(i - 1),
                    String.format(
                            "Position should increase monotonically: %d -> %d",
                            reportedPositionsMs.get(i - 1), reportedPositionsMs.get(i)));
        }

        // Verify positions are approximately correct (within 200ms of expected)
        // After 1 second of playback, position should be ~1000ms
        for (int i = 0; i < Math.min(updateTimesMs.size(), reportedPositionsMs.size()); i++) {
            long expectedPositionMs = updateTimesMs.get(i);
            long actualPositionMs = reportedPositionsMs.get(i);
            long difference = Math.abs(expectedPositionMs - actualPositionMs);

            // Allow 200ms tolerance for position accuracy
            assertTrue(
                    difference <= 200,
                    String.format(
                            "At time %dms, position should be ~%dms but was %dms (diff: %dms)",
                            updateTimesMs.get(i),
                            expectedPositionMs,
                            actualPositionMs,
                            difference));
        }

        System.out.printf(
                "Progress timing test: %d updates over %.1f seconds%n",
                updateTimesMs.size(), updateTimesMs.get(updateTimesMs.size() - 1) / 1000.0);
        System.out.printf(
                "Average interval: %.1fms%n",
                updateTimesMs.stream()
                        .skip(1)
                        .mapToLong(t -> t - updateTimesMs.get(updateTimesMs.indexOf(t) - 1))
                        .average()
                        .orElse(0));
    }

    @Test
    void testStateChangeNotification() {
        TestListener listener1 = new TestListener();
        TestListener listener2 = new TestListener();

        listenerManager.addListener(listener1);
        listenerManager.addListener(listener2);

        Pointer mockSound = new Memory(8);
        FmodAudioHandle mockAudioHandle = new FmodAudioHandle(1, mockSound, "test.wav");
        Pointer mockChannel = new Memory(8);
        FmodPlaybackHandle mockHandle =
                new FmodPlaybackHandle(mockAudioHandle, mockChannel, 0, 100);

        // Send various state changes
        listenerManager.notifyStateChanged(
                mockHandle, PlaybackState.PLAYING, PlaybackState.STOPPED);
        listenerManager.notifyStateChanged(mockHandle, PlaybackState.PAUSED, PlaybackState.PLAYING);
        listenerManager.notifyStateChanged(mockHandle, PlaybackState.PLAYING, PlaybackState.PAUSED);
        listenerManager.notifyStateChanged(
                mockHandle, PlaybackState.FINISHED, PlaybackState.PLAYING);

        // Both listeners should receive all
        assertEquals(4, listener1.stateChanges.size());
        assertEquals(4, listener2.stateChanges.size());

        // Verify sequence
        assertEquals(PlaybackState.PLAYING, listener1.stateChanges.get(0).newState);
        assertEquals(PlaybackState.PAUSED, listener1.stateChanges.get(1).newState);
        assertEquals(PlaybackState.PLAYING, listener1.stateChanges.get(2).newState);
        assertEquals(PlaybackState.FINISHED, listener1.stateChanges.get(3).newState);
    }

    @Test
    void testCompletionNotificationSequence() {
        TestListener listener = new TestListener();
        listenerManager.addListener(listener);

        Pointer mockSound = new Memory(8);
        FmodAudioHandle mockAudioHandle = new FmodAudioHandle(1, mockSound, "test.wav");
        Pointer mockChannel = new Memory(8);
        FmodPlaybackHandle mockHandle =
                new FmodPlaybackHandle(mockAudioHandle, mockChannel, 0, 100);

        // Notify completion
        listenerManager.notifyPlaybackComplete(mockHandle);

        // Should receive FINISHED state first, then completion
        assertEquals(1, listener.stateChanges.size());
        assertEquals(PlaybackState.FINISHED, listener.stateChanges.get(0).newState);
        assertEquals(PlaybackState.PLAYING, listener.stateChanges.get(0).oldState);
        assertEquals(1, listener.completeCount.get());
    }

    // ========== Thread Safety & Shutdown Tests ==========

    @Test
    @Timeout(5)
    void testConcurrentListenerOperations() throws Exception {
        AtomicInteger addCount = new AtomicInteger();
        AtomicInteger removeCount = new AtomicInteger();
        AtomicBoolean error = new AtomicBoolean();

        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(5);

        // Thread 1-2: Add listeners
        for (int i = 0; i < 2; i++) {
            executor.submit(
                    () -> {
                        try {
                            startLatch.await();
                            for (int j = 0; j < 50; j++) {
                                TestListener listener = new TestListener();
                                listenerManager.addListener(listener);
                                addCount.incrementAndGet();
                                Thread.sleep(1);
                                listenerManager.removeListener(listener);
                                removeCount.incrementAndGet();
                            }
                        } catch (Exception e) {
                            error.set(true);
                        } finally {
                            doneLatch.countDown();
                        }
                    });
        }

        // Thread 3-5: Send notifications
        AtomicInteger idCounter = new AtomicInteger();
        for (int i = 0; i < 3; i++) {
            executor.submit(
                    () -> {
                        try {
                            startLatch.await();
                            Pointer mockSound = new Memory(8);
                            FmodAudioHandle mockAudioHandle =
                                    new FmodAudioHandle(
                                            idCounter.incrementAndGet(), mockSound, "test.wav");
                            Pointer mockChannel = new Memory(8);
                            FmodPlaybackHandle mockHandle =
                                    new FmodPlaybackHandle(mockAudioHandle, mockChannel, 0, 100);
                            for (int j = 0; j < 100; j++) {
                                listenerManager.notifyProgress(mockHandle, j * 1000, 100000);
                                Thread.sleep(1);
                            }
                        } catch (Exception e) {
                            error.set(true);
                            e.printStackTrace();
                        } finally {
                            doneLatch.countDown();
                        }
                    });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(3, TimeUnit.SECONDS));
        assertFalse(error.get());

        // Should end with no listeners
        assertEquals(addCount.get(), removeCount.get());
        assertEquals(0, listenerManager.getListenerCount());

        executor.shutdown();
    }

    @Test
    void testShutdownStopsEverything() throws Exception {
        TestListener listener = new TestListener();
        listenerManager.addListener(listener);

        // Load and play audio
        loadingManager.loadAudio(SAMPLE_WAV);
        Pointer sound =
                loadingManager
                        .getCurrentSound()
                        .orElseThrow(() -> new IllegalStateException("Sound not loaded"));
        FmodPlaybackHandle handle =
                playbackManager.play(sound, loadingManager.getCurrentHandle().get());

        // Start monitoring
        listenerManager.startMonitoring(handle, 1000000);

        // Should be active
        assertTrue(listenerManager.hasListeners());
        assertFalse(listenerManager.isShutdown());

        // Shutdown
        listenerManager.shutdown();

        // Should be shutdown
        assertTrue(listenerManager.isShutdown());
        assertFalse(listenerManager.hasListeners());
        assertEquals(0, listenerManager.getListenerCount());

        // Can't add new listeners
        TestListener newListener = new TestListener();
        listenerManager.addListener(newListener);
        assertEquals(0, listenerManager.getListenerCount());

        // Can't start monitoring
        listenerManager.startMonitoring(handle, 1000000);
        Thread.sleep(PROGRESS_INTERVAL_MS * 3);
        assertEquals(0, newListener.progressUpdates.size());

        // Shutdown again should be safe
        listenerManager.shutdown();

        playbackManager.stop();
    }

    // ========== Edge Cases ==========

    @Test
    @Timeout(5)
    void testRapidMonitoringChanges() throws Exception {
        TestListener listener = new TestListener();
        listenerManager.addListener(listener);

        // Load audio
        loadingManager.loadAudio(SAMPLE_WAV);
        Pointer sound =
                loadingManager
                        .getCurrentSound()
                        .orElseThrow(() -> new IllegalStateException("Sound not loaded"));

        // Rapid start/stop cycles
        for (int i = 0; i < 10; i++) {
            FmodPlaybackHandle handle =
                    playbackManager.play(sound, loadingManager.getCurrentHandle().get());
            listenerManager.startMonitoring(handle, 1000000);
            Thread.sleep(10);
            listenerManager.stopMonitoring();
            playbackManager.stop();
        }

        // Should not crash or leak resources
        assertFalse(listenerManager.isShutdown());
    }

    @Test
    @Timeout(5)
    void testInactiveHandleImmediateCompletion() throws Exception {
        TestListener listener = new TestListener();
        listenerManager.addListener(listener);

        // Create a handle and immediately mark it inactive
        Pointer mockSound = new Memory(8);
        Pointer mockChannel = new Memory(8);
        FmodAudioHandle mockAudioHandle = new FmodAudioHandle(1, mockSound, "test.wav");
        FmodPlaybackHandle handle = new FmodPlaybackHandle(mockAudioHandle, mockChannel, 0, 100);
        handle.markInactive();

        // Start monitoring with inactive handle
        listenerManager.startMonitoring(handle, 1000000);

        // Should receive completion immediately
        assertTrue(listener.waitForCompletion(1, TimeUnit.SECONDS));
        assertEquals(1, listener.completeCount.get());
    }

    @Test
    void testNoListenersOptimization() throws Exception {
        // Load and play audio
        loadingManager.loadAudio(SAMPLE_WAV);
        Pointer sound =
                loadingManager
                        .getCurrentSound()
                        .orElseThrow(() -> new IllegalStateException("Sound not loaded"));
        FmodPlaybackHandle handle =
                playbackManager.play(sound, loadingManager.getCurrentHandle().get());

        // Start monitoring with no listeners
        listenerManager.startMonitoring(handle, 1000000);

        // updateProgress should return early without querying FMOD
        // We can't directly verify this, but it shouldn't crash
        listenerManager.updateProgress();

        playbackManager.stop();
    }
}
