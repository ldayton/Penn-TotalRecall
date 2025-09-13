package core.audio;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Slf4j
class AudioSessionStateManagerTest {

    private AudioSessionStateMachine stateManager;

    @BeforeEach
    void setUp() {
        stateManager = new AudioSessionStateMachine();
    }

    @Test
    void testInitialState() {
        assertEquals(AudioSessionStateMachine.State.NO_AUDIO, stateManager.getCurrentState());
        assertFalse(stateManager.isAudioLoaded());
        assertFalse(stateManager.isPlaybackActive());
    }

    @Test
    void testValidTransitions() {
        // NO_AUDIO -> LOADING
        assertTrue(
                stateManager.compareAndSetState(
                        AudioSessionStateMachine.State.NO_AUDIO,
                        AudioSessionStateMachine.State.LOADING));
        assertEquals(AudioSessionStateMachine.State.LOADING, stateManager.getCurrentState());
        assertFalse(stateManager.isAudioLoaded());

        // LOADING -> READY
        assertTrue(
                stateManager.compareAndSetState(
                        AudioSessionStateMachine.State.LOADING,
                        AudioSessionStateMachine.State.READY));
        assertEquals(AudioSessionStateMachine.State.READY, stateManager.getCurrentState());
        assertTrue(stateManager.isAudioLoaded());
        assertFalse(stateManager.isPlaybackActive());

        // READY -> PLAYING
        assertTrue(
                stateManager.compareAndSetState(
                        AudioSessionStateMachine.State.READY,
                        AudioSessionStateMachine.State.PLAYING));
        assertEquals(AudioSessionStateMachine.State.PLAYING, stateManager.getCurrentState());
        assertTrue(stateManager.isAudioLoaded());
        assertTrue(stateManager.isPlaybackActive());

        // PLAYING -> PAUSED
        assertTrue(
                stateManager.compareAndSetState(
                        AudioSessionStateMachine.State.PLAYING,
                        AudioSessionStateMachine.State.PAUSED));
        assertEquals(AudioSessionStateMachine.State.PAUSED, stateManager.getCurrentState());
        assertTrue(stateManager.isPlaybackActive());

        // PAUSED -> PLAYING (resume)
        assertTrue(
                stateManager.compareAndSetState(
                        AudioSessionStateMachine.State.PAUSED,
                        AudioSessionStateMachine.State.PLAYING));
        assertEquals(AudioSessionStateMachine.State.PLAYING, stateManager.getCurrentState());

        // PLAYING -> READY (stop)
        assertTrue(
                stateManager.compareAndSetState(
                        AudioSessionStateMachine.State.PLAYING,
                        AudioSessionStateMachine.State.READY));
        assertEquals(AudioSessionStateMachine.State.READY, stateManager.getCurrentState());

        // READY -> NO_AUDIO (close)
        assertTrue(
                stateManager.compareAndSetState(
                        AudioSessionStateMachine.State.READY,
                        AudioSessionStateMachine.State.NO_AUDIO));
        assertEquals(AudioSessionStateMachine.State.NO_AUDIO, stateManager.getCurrentState());
    }

    @Test
    void testInvalidTransitions() {
        // NO_AUDIO -> PLAYING (skipping LOADING and READY)
        assertFalse(
                stateManager.compareAndSetState(
                        AudioSessionStateMachine.State.NO_AUDIO,
                        AudioSessionStateMachine.State.PLAYING));

        // NO_AUDIO -> PAUSED
        assertFalse(
                stateManager.compareAndSetState(
                        AudioSessionStateMachine.State.NO_AUDIO,
                        AudioSessionStateMachine.State.PAUSED));

        // NO_AUDIO -> READY (skipping LOADING)
        assertFalse(
                stateManager.compareAndSetState(
                        AudioSessionStateMachine.State.NO_AUDIO,
                        AudioSessionStateMachine.State.READY));

        // Move to LOADING
        stateManager.transitionToLoading();

        // LOADING -> PLAYING (skipping READY)
        assertFalse(
                stateManager.compareAndSetState(
                        AudioSessionStateMachine.State.LOADING,
                        AudioSessionStateMachine.State.PLAYING));

        // LOADING -> PAUSED
        assertFalse(
                stateManager.compareAndSetState(
                        AudioSessionStateMachine.State.LOADING,
                        AudioSessionStateMachine.State.PAUSED));
    }

    @Test
    void testTransitionMethods() {
        // Test transitionToLoading
        stateManager.transitionToLoading();
        assertEquals(AudioSessionStateMachine.State.LOADING, stateManager.getCurrentState());

        // Test transitionToReady
        stateManager.transitionToReady();
        assertEquals(AudioSessionStateMachine.State.READY, stateManager.getCurrentState());

        // Test transitionToPlaying
        stateManager.transitionToPlaying();
        assertEquals(AudioSessionStateMachine.State.PLAYING, stateManager.getCurrentState());

        // Test transitionToPaused
        stateManager.transitionToPaused();
        assertEquals(AudioSessionStateMachine.State.PAUSED, stateManager.getCurrentState());

        // Test resume (PAUSED -> PLAYING)
        stateManager.transitionToPlaying();
        assertEquals(AudioSessionStateMachine.State.PLAYING, stateManager.getCurrentState());

        // Test stop (PLAYING -> READY)
        stateManager.transitionToReady();
        assertEquals(AudioSessionStateMachine.State.READY, stateManager.getCurrentState());

        // Test transitionToNoAudio
        stateManager.transitionToNoAudio();
        assertEquals(AudioSessionStateMachine.State.NO_AUDIO, stateManager.getCurrentState());
    }

    @Test
    void testErrorStateTransitions() {
        // LOADING -> ERROR
        stateManager.transitionToLoading();
        stateManager.transitionToError();
        assertEquals(AudioSessionStateMachine.State.ERROR, stateManager.getCurrentState());
        assertFalse(stateManager.isAudioLoaded());
        assertFalse(stateManager.isPlaybackActive());

        // ERROR -> NO_AUDIO (reset)
        stateManager.transitionToNoAudio();
        assertEquals(AudioSessionStateMachine.State.NO_AUDIO, stateManager.getCurrentState());

        // ERROR -> LOADING (retry)
        stateManager.forceError(); // Use forceError to get back to ERROR state
        stateManager.transitionToLoading();
        assertEquals(AudioSessionStateMachine.State.LOADING, stateManager.getCurrentState());

        // PLAYING -> ERROR
        stateManager.transitionToReady();
        stateManager.transitionToPlaying();
        stateManager.transitionToError();
        assertEquals(AudioSessionStateMachine.State.ERROR, stateManager.getCurrentState());
    }

    @Test
    void testInvalidTransitionExceptions() {
        // Try to pause when not playing
        assertThrows(IllegalStateException.class, () -> stateManager.transitionToPaused());

        // Try to play when no audio
        assertThrows(IllegalStateException.class, () -> stateManager.transitionToPlaying());

        // Try to transition to ready from NO_AUDIO
        assertThrows(IllegalStateException.class, () -> stateManager.transitionToReady());

        // Try to transition to error from READY
        stateManager.transitionToLoading();
        stateManager.transitionToReady();
        assertThrows(IllegalStateException.class, () -> stateManager.transitionToError());
    }

    @Test
    void testExecuteInState() {
        // Move to READY state
        stateManager.transitionToLoading();
        stateManager.transitionToReady();

        // Execute action in correct state
        AtomicInteger result = new AtomicInteger();
        stateManager.executeInState(
                AudioSessionStateMachine.State.READY,
                () -> {
                    result.set(42);
                });
        assertEquals(42, result.get());

        // Try to execute in wrong state
        assertThrows(
                IllegalStateException.class,
                () ->
                        stateManager.executeInState(
                                AudioSessionStateMachine.State.PLAYING, () -> {}));
    }

    @Test
    void testCheckStateAny() {
        stateManager.transitionToLoading();
        stateManager.transitionToReady();

        // Should pass - current state is one of the expected
        assertDoesNotThrow(
                () ->
                        stateManager.checkStateAny(
                                AudioSessionStateMachine.State.READY,
                                AudioSessionStateMachine.State.PLAYING));

        // Should fail - current state is not in the list
        assertThrows(
                IllegalStateException.class,
                () ->
                        stateManager.checkStateAny(
                                AudioSessionStateMachine.State.LOADING,
                                AudioSessionStateMachine.State.NO_AUDIO));
    }

    @Test
    void testReset() {
        // Get to PLAYING state
        stateManager.transitionToLoading();
        stateManager.transitionToReady();
        stateManager.transitionToPlaying();
        assertEquals(AudioSessionStateMachine.State.PLAYING, stateManager.getCurrentState());

        // Reset should go back to NO_AUDIO
        stateManager.reset();
        assertEquals(AudioSessionStateMachine.State.NO_AUDIO, stateManager.getCurrentState());
    }

    @Test
    void testForceError() {
        // From NO_AUDIO
        stateManager.forceError();
        assertEquals(AudioSessionStateMachine.State.ERROR, stateManager.getCurrentState());

        // Force error when already in error should be idempotent
        stateManager.forceError();
        assertEquals(AudioSessionStateMachine.State.ERROR, stateManager.getCurrentState());

        // From PLAYING
        stateManager.reset();
        stateManager.transitionToLoading();
        stateManager.transitionToReady();
        stateManager.transitionToPlaying();
        stateManager.forceError();
        assertEquals(AudioSessionStateMachine.State.ERROR, stateManager.getCurrentState());
    }

    @Test
    @Timeout(5)
    void testConcurrentStateChanges() throws InterruptedException {
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(
                    () -> {
                        try {
                            startLatch.await();
                            // Only one thread should succeed
                            if (stateManager.compareAndSetState(
                                    AudioSessionStateMachine.State.NO_AUDIO,
                                    AudioSessionStateMachine.State.LOADING)) {
                                successCount.incrementAndGet();
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            doneLatch.countDown();
                        }
                    });
        }

        startLatch.countDown(); // Release all threads
        assertTrue(doneLatch.await(3, TimeUnit.SECONDS));

        // Only one thread should have succeeded
        assertEquals(1, successCount.get());
        assertEquals(AudioSessionStateMachine.State.LOADING, stateManager.getCurrentState());

        executor.shutdown();
    }

    @Test
    void testExecuteWithLock() {
        AtomicReference<AudioSessionStateMachine.State> capturedState = new AtomicReference<>();

        AudioSessionStateMachine.State result =
                stateManager.executeWithLock(
                        () -> {
                            capturedState.set(stateManager.getCurrentState());
                            return stateManager.getCurrentState();
                        });

        assertEquals(AudioSessionStateMachine.State.NO_AUDIO, result);
        assertEquals(AudioSessionStateMachine.State.NO_AUDIO, capturedState.get());
    }

    @Test
    void testIsAudioLoaded() {
        assertFalse(stateManager.isAudioLoaded());

        stateManager.transitionToLoading();
        assertFalse(stateManager.isAudioLoaded());

        stateManager.transitionToReady();
        assertTrue(stateManager.isAudioLoaded());

        stateManager.transitionToPlaying();
        assertTrue(stateManager.isAudioLoaded());

        stateManager.transitionToPaused();
        assertTrue(stateManager.isAudioLoaded());

        stateManager.transitionToReady(); // stop
        assertTrue(stateManager.isAudioLoaded());

        stateManager.transitionToNoAudio();
        assertFalse(stateManager.isAudioLoaded());
    }

    @Test
    void testIsPlaybackActive() {
        assertFalse(stateManager.isPlaybackActive());

        stateManager.transitionToLoading();
        assertFalse(stateManager.isPlaybackActive());

        stateManager.transitionToReady();
        assertFalse(stateManager.isPlaybackActive());

        stateManager.transitionToPlaying();
        assertTrue(stateManager.isPlaybackActive());

        stateManager.transitionToPaused();
        assertTrue(stateManager.isPlaybackActive());

        stateManager.transitionToReady(); // stop
        assertFalse(stateManager.isPlaybackActive());
    }

    @Test
    @Timeout(5)
    void testStressTestWithManyThreads() throws InterruptedException {
        int threadCount = 100;
        int operationsPerThread = 100;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successfulOps = new AtomicInteger(0);
        AtomicInteger failedOps = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(20);

        // First, get to READY state
        stateManager.transitionToLoading();
        stateManager.transitionToReady();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(
                    () -> {
                        try {
                            startLatch.await();
                            for (int j = 0; j < operationsPerThread; j++) {
                                try {
                                    stateManager.executeInState(
                                            AudioSessionStateMachine.State.READY,
                                            () -> {
                                                // Simulate some work
                                                successfulOps.incrementAndGet();
                                            });
                                } catch (IllegalStateException e) {
                                    failedOps.incrementAndGet();
                                }
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            doneLatch.countDown();
                        }
                    });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(3, TimeUnit.SECONDS));

        // All operations should succeed since we're in the right state
        assertEquals(threadCount * operationsPerThread, successfulOps.get());
        assertEquals(0, failedOps.get());

        executor.shutdown();
    }

    @Test
    void testMemoryVisibilityAcrossThreads() throws InterruptedException {
        // Transition to PLAYING in one thread
        Thread writer =
                new Thread(
                        () -> {
                            stateManager.transitionToLoading();
                            stateManager.transitionToReady();
                            stateManager.transitionToPlaying();
                        });

        writer.start();
        writer.join();

        // Read in multiple threads - should all see PLAYING
        int readerCount = 10;
        CountDownLatch latch = new CountDownLatch(readerCount);
        AtomicInteger correctReads = new AtomicInteger(0);

        for (int i = 0; i < readerCount; i++) {
            new Thread(
                            () -> {
                                if (stateManager.getCurrentState()
                                        == AudioSessionStateMachine.State.PLAYING) {
                                    correctReads.incrementAndGet();
                                }
                                latch.countDown();
                            })
                    .start();
        }

        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(readerCount, correctReads.get(), "Memory visibility issue detected");
    }

    @Test
    void testLockIsReleasedOnException() throws InterruptedException {
        // Cause an exception while holding the lock
        assertThrows(
                RuntimeException.class,
                () -> {
                    stateManager.executeWithLock(
                            () -> {
                                throw new RuntimeException("Test");
                            });
                });

        // Lock should be released - we should be able to acquire it again
        AtomicBoolean lockAcquired = new AtomicBoolean(false);
        Thread thread =
                new Thread(
                        () -> {
                            stateManager.executeWithLock(
                                    () -> {
                                        lockAcquired.set(true);
                                        return null;
                                    });
                        });

        thread.start();
        thread.join(100);

        assertTrue(lockAcquired.get(), "Lock was not released after exception");
    }

    @Test
    void testAllStatesReachable() {
        // Verify we can reach every state

        // Start at NO_AUDIO
        assertEquals(AudioSessionStateMachine.State.NO_AUDIO, stateManager.getCurrentState());

        // Can reach LOADING
        assertTrue(
                stateManager.compareAndSetState(
                        AudioSessionStateMachine.State.NO_AUDIO,
                        AudioSessionStateMachine.State.LOADING));

        // Can reach READY
        assertTrue(
                stateManager.compareAndSetState(
                        AudioSessionStateMachine.State.LOADING,
                        AudioSessionStateMachine.State.READY));

        // Can reach PLAYING
        assertTrue(
                stateManager.compareAndSetState(
                        AudioSessionStateMachine.State.READY,
                        AudioSessionStateMachine.State.PLAYING));

        // Can reach PAUSED
        assertTrue(
                stateManager.compareAndSetState(
                        AudioSessionStateMachine.State.PLAYING,
                        AudioSessionStateMachine.State.PAUSED));

        // Can reach ERROR (via forceError since normal transition requires specific states)
        stateManager.forceError();
        assertEquals(AudioSessionStateMachine.State.ERROR, stateManager.getCurrentState());

        // ERROR can go to NO_AUDIO
        assertTrue(
                stateManager.compareAndSetState(
                        AudioSessionStateMachine.State.ERROR,
                        AudioSessionStateMachine.State.NO_AUDIO));

        // Verify all 6 states were reachable
    }

    @Test
    void testCompletePlaybackCycle() {
        // Simulate a complete audio playback cycle

        // Load audio
        stateManager.transitionToLoading();
        assertEquals(AudioSessionStateMachine.State.LOADING, stateManager.getCurrentState());

        // Loading completes
        stateManager.transitionToReady();
        assertEquals(AudioSessionStateMachine.State.READY, stateManager.getCurrentState());

        // Start playback
        stateManager.transitionToPlaying();
        assertEquals(AudioSessionStateMachine.State.PLAYING, stateManager.getCurrentState());

        // Pause playback
        stateManager.transitionToPaused();
        assertEquals(AudioSessionStateMachine.State.PAUSED, stateManager.getCurrentState());

        // Resume playback
        stateManager.transitionToPlaying();
        assertEquals(AudioSessionStateMachine.State.PLAYING, stateManager.getCurrentState());

        // Stop playback
        stateManager.transitionToReady();
        assertEquals(AudioSessionStateMachine.State.READY, stateManager.getCurrentState());

        // Close audio
        stateManager.transitionToNoAudio();
        assertEquals(AudioSessionStateMachine.State.NO_AUDIO, stateManager.getCurrentState());
    }

    @Test
    void testErrorRecoveryCycle() {
        // Simulate error and recovery

        // Start loading
        stateManager.transitionToLoading();

        // Loading fails
        stateManager.transitionToError();
        assertEquals(AudioSessionStateMachine.State.ERROR, stateManager.getCurrentState());

        // Reset to NO_AUDIO
        stateManager.transitionToNoAudio();
        assertEquals(AudioSessionStateMachine.State.NO_AUDIO, stateManager.getCurrentState());

        // Retry loading
        stateManager.transitionToLoading();
        assertEquals(AudioSessionStateMachine.State.LOADING, stateManager.getCurrentState());

        // This time it succeeds
        stateManager.transitionToReady();
        assertEquals(AudioSessionStateMachine.State.READY, stateManager.getCurrentState());
    }

    @Test
    void testFileSwitching() {
        // Test switching between audio files

        // Load first file
        stateManager.transitionToLoading();
        stateManager.transitionToReady();
        stateManager.transitionToPlaying();

        // Stop playback to switch files
        stateManager.transitionToReady();

        // Switch to new file (READY -> LOADING)
        stateManager.transitionToLoading();
        assertEquals(AudioSessionStateMachine.State.LOADING, stateManager.getCurrentState());

        // New file loaded
        stateManager.transitionToReady();
        assertEquals(AudioSessionStateMachine.State.READY, stateManager.getCurrentState());
    }
}
