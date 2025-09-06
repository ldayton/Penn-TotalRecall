package audio.fmod;

import static org.junit.jupiter.api.Assertions.*;

import audio.PlaybackState;
import audio.exceptions.AudioPlaybackException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class FmodPlaybackStateManagerTest {

    private FmodPlaybackStateManager stateManager;

    @BeforeEach
    void setUp() {
        stateManager = new FmodPlaybackStateManager();
    }

    @Test
    void testInitialState() {
        assertEquals(PlaybackState.STOPPED, stateManager.getCurrentState());
        assertFalse(stateManager.isActive());
    }

    @Test
    void testValidTransitions() {
        // STOPPED -> PLAYING
        assertDoesNotThrow(() -> stateManager.transitionToPlaying());
        assertEquals(PlaybackState.PLAYING, stateManager.getCurrentState());
        assertTrue(stateManager.isActive());

        // PLAYING -> PAUSED
        assertDoesNotThrow(() -> stateManager.transitionToPaused());
        assertEquals(PlaybackState.PAUSED, stateManager.getCurrentState());
        assertTrue(stateManager.isActive());

        // PAUSED -> PLAYING (resume)
        assertDoesNotThrow(() -> stateManager.resume());
        assertEquals(PlaybackState.PLAYING, stateManager.getCurrentState());

        // PLAYING -> STOPPED
        assertDoesNotThrow(() -> stateManager.transitionToStopped());
        assertEquals(PlaybackState.STOPPED, stateManager.getCurrentState());
        assertFalse(stateManager.isActive());

        // Can start again from STOPPED
        assertDoesNotThrow(() -> stateManager.transitionToPlaying());
        assertEquals(PlaybackState.PLAYING, stateManager.getCurrentState());

        // PLAYING -> FINISHED
        assertDoesNotThrow(() -> stateManager.transitionToFinished());
        assertEquals(PlaybackState.FINISHED, stateManager.getCurrentState());
        assertFalse(stateManager.isActive());

        // FINISHED -> PLAYING (can restart from finished)
        assertDoesNotThrow(() -> stateManager.transitionToPlaying());
        assertEquals(PlaybackState.PLAYING, stateManager.getCurrentState());
    }

    @Test
    void testInvalidTransitions() {
        // Cannot pause from STOPPED
        assertThrows(AudioPlaybackException.class, () -> stateManager.transitionToPaused());

        // Cannot resume from STOPPED
        assertThrows(AudioPlaybackException.class, () -> stateManager.resume());

        // Cannot finish from STOPPED
        assertThrows(AudioPlaybackException.class, () -> stateManager.transitionToFinished());

        // Start playing
        stateManager.transitionToPlaying();

        // Cannot play while already playing
        assertThrows(AudioPlaybackException.class, () -> stateManager.transitionToPlaying());

        // Move to paused
        stateManager.transitionToPaused();

        // Cannot pause while already paused
        assertThrows(AudioPlaybackException.class, () -> stateManager.transitionToPaused());

        // Cannot finish from paused
        assertThrows(AudioPlaybackException.class, () -> stateManager.transitionToFinished());
    }

    @Test
    void testSeekValidation() {
        // Cannot seek from STOPPED
        assertThrows(AudioPlaybackException.class, () -> stateManager.validateSeekAllowed());

        // Can seek from PLAYING
        stateManager.transitionToPlaying();
        assertDoesNotThrow(() -> stateManager.validateSeekAllowed());
        assertEquals(PlaybackState.PLAYING, stateManager.getCurrentState()); // State unchanged

        // Can seek from PAUSED
        stateManager.transitionToPaused();
        assertDoesNotThrow(() -> stateManager.validateSeekAllowed());
        assertEquals(PlaybackState.PAUSED, stateManager.getCurrentState()); // State unchanged

        // Cannot seek from STOPPED
        stateManager.transitionToStopped();
        assertThrows(AudioPlaybackException.class, () -> stateManager.validateSeekAllowed());

        // Cannot seek from FINISHED
        stateManager.transitionToPlaying();
        stateManager.transitionToFinished();
        assertThrows(AudioPlaybackException.class, () -> stateManager.validateSeekAllowed());
    }

    @Test
    void testHandleChannelInvalid() {
        // From PLAYING -> forced to STOPPED
        stateManager.transitionToPlaying();
        stateManager.handleChannelInvalid();
        assertEquals(PlaybackState.STOPPED, stateManager.getCurrentState());

        // From PAUSED -> forced to STOPPED
        stateManager.transitionToPlaying();
        stateManager.transitionToPaused();
        stateManager.handleChannelInvalid();
        assertEquals(PlaybackState.STOPPED, stateManager.getCurrentState());

        // From STOPPED -> stays STOPPED
        stateManager.handleChannelInvalid();
        assertEquals(PlaybackState.STOPPED, stateManager.getCurrentState());

        // From FINISHED -> stays FINISHED
        stateManager.transitionToPlaying();
        stateManager.transitionToFinished();
        stateManager.handleChannelInvalid();
        assertEquals(PlaybackState.FINISHED, stateManager.getCurrentState());
    }

    @Test
    void testReset() {
        // Reset from various states
        stateManager.transitionToPlaying();
        stateManager.reset();
        assertEquals(PlaybackState.STOPPED, stateManager.getCurrentState());

        stateManager.transitionToPlaying();
        stateManager.transitionToPaused();
        stateManager.reset();
        assertEquals(PlaybackState.STOPPED, stateManager.getCurrentState());

        stateManager.transitionToPlaying();
        stateManager.transitionToFinished();
        stateManager.reset();
        assertEquals(PlaybackState.STOPPED, stateManager.getCurrentState());

        // Reset when already stopped
        stateManager.reset();
        assertEquals(PlaybackState.STOPPED, stateManager.getCurrentState());
    }

    @Test
    void testCheckState() {
        stateManager.checkState(PlaybackState.STOPPED); // Should not throw

        assertThrows(
                AudioPlaybackException.class, () -> stateManager.checkState(PlaybackState.PLAYING));

        stateManager.transitionToPlaying();
        stateManager.checkState(PlaybackState.PLAYING); // Should not throw

        assertThrows(
                AudioPlaybackException.class, () -> stateManager.checkState(PlaybackState.PAUSED));
    }

    @Test
    void testCompareAndSetState() {
        // Successful transition
        assertTrue(stateManager.compareAndSetState(PlaybackState.STOPPED, PlaybackState.PLAYING));
        assertEquals(PlaybackState.PLAYING, stateManager.getCurrentState());

        // Failed - wrong expected state
        assertFalse(stateManager.compareAndSetState(PlaybackState.STOPPED, PlaybackState.PAUSED));
        assertEquals(PlaybackState.PLAYING, stateManager.getCurrentState()); // Unchanged

        // Invalid transition attempt (PAUSED -> FINISHED is not allowed)
        stateManager.transitionToPaused();
        assertFalse(stateManager.compareAndSetState(PlaybackState.PAUSED, PlaybackState.FINISHED));
        assertEquals(PlaybackState.PAUSED, stateManager.getCurrentState()); // Unchanged

        // Valid transition
        assertTrue(stateManager.compareAndSetState(PlaybackState.PAUSED, PlaybackState.PLAYING));
        assertEquals(PlaybackState.PLAYING, stateManager.getCurrentState());
    }

    @Test
    void testIsActive() {
        assertFalse(stateManager.isActive()); // STOPPED

        stateManager.transitionToPlaying();
        assertTrue(stateManager.isActive()); // PLAYING

        stateManager.transitionToPaused();
        assertTrue(stateManager.isActive()); // PAUSED

        stateManager.transitionToStopped();
        assertFalse(stateManager.isActive()); // STOPPED

        stateManager.transitionToPlaying();
        stateManager.transitionToFinished();
        assertFalse(stateManager.isActive()); // FINISHED
    }

    @Test
    void testStopFromAlreadyStopped() {
        // Should not throw when already stopped
        assertDoesNotThrow(() -> stateManager.transitionToStopped());
        assertEquals(PlaybackState.STOPPED, stateManager.getCurrentState());
    }

    @Test
    void testTransitionToStoppedFromAnyState() {
        // transitionToStopped should work from ANY state except already STOPPED

        // From PLAYING -> STOPPED
        stateManager.transitionToPlaying();
        assertDoesNotThrow(() -> stateManager.transitionToStopped());
        assertEquals(PlaybackState.STOPPED, stateManager.getCurrentState());

        // From PAUSED -> STOPPED
        stateManager.transitionToPlaying();
        stateManager.transitionToPaused();
        assertDoesNotThrow(() -> stateManager.transitionToStopped());
        assertEquals(PlaybackState.STOPPED, stateManager.getCurrentState());

        // From FINISHED -> STOPPED (THIS IS THE KEY TEST - desired behavior)
        stateManager.transitionToPlaying();
        stateManager.transitionToFinished();
        assertDoesNotThrow(() -> stateManager.transitionToStopped());
        assertEquals(
                PlaybackState.STOPPED,
                stateManager.getCurrentState(),
                "transitionToStopped() MUST work from FINISHED state");

        // From STOPPED -> STOPPED (no-op, but shouldn't throw)
        assertDoesNotThrow(() -> stateManager.transitionToStopped());
        assertEquals(PlaybackState.STOPPED, stateManager.getCurrentState());
    }

    @Test
    @Timeout(5)
    void testConcurrentStateChanges() throws InterruptedException {
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // Multiple threads trying to transition from STOPPED to PLAYING
        for (int i = 0; i < threadCount; i++) {
            executor.submit(
                    () -> {
                        try {
                            startLatch.await();
                            try {
                                stateManager.transitionToPlaying();
                                successCount.incrementAndGet();
                            } catch (AudioPlaybackException e) {
                                // Expected for all but one thread
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

        // Only one thread should have succeeded
        assertEquals(1, successCount.get());
        assertEquals(PlaybackState.PLAYING, stateManager.getCurrentState());

        executor.shutdown();
    }

    @Test
    @Timeout(5)
    void testStressTestWithManyThreads() throws InterruptedException {
        int threadCount = 50;
        int operationsPerThread = 100;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        ExecutorService executor = Executors.newFixedThreadPool(10);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(
                    () -> {
                        try {
                            startLatch.await();
                            for (int j = 0; j < operationsPerThread; j++) {
                                // Mix of operations based on thread ID
                                try {
                                    switch (threadId % 5) {
                                        case 0 -> {
                                            if (stateManager.getCurrentState()
                                                    == PlaybackState.STOPPED) {
                                                stateManager.transitionToPlaying();
                                            }
                                        }
                                        case 1 -> {
                                            if (stateManager.getCurrentState()
                                                    == PlaybackState.PLAYING) {
                                                stateManager.transitionToPaused();
                                            }
                                        }
                                        case 2 -> {
                                            if (stateManager.getCurrentState()
                                                    == PlaybackState.PAUSED) {
                                                stateManager.resume();
                                            }
                                        }
                                        case 3 -> stateManager.transitionToStopped();
                                        case 4 -> stateManager.getCurrentState(); // Just read
                                    }
                                } catch (AudioPlaybackException e) {
                                    // Expected for invalid transitions
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
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));

        // Should end in a valid state
        PlaybackState finalState = stateManager.getCurrentState();
        assertNotNull(finalState);
        // Any state is valid as long as the state machine didn't corrupt

        executor.shutdown();
    }

    @Test
    void testMemoryVisibilityAcrossThreads() throws InterruptedException {
        // Transition to PLAYING in one thread
        Thread writer =
                new Thread(
                        () -> {
                            stateManager.transitionToPlaying();
                            stateManager.transitionToPaused();
                        });

        writer.start();
        writer.join();

        // Read in multiple threads - should all see PAUSED
        int readerCount = 10;
        CountDownLatch latch = new CountDownLatch(readerCount);
        AtomicInteger correctReads = new AtomicInteger(0);

        for (int i = 0; i < readerCount; i++) {
            new Thread(
                            () -> {
                                if (stateManager.getCurrentState() == PlaybackState.PAUSED) {
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
    void testAllValidStateTransitions() {
        // Test the isValidTransition logic indirectly through compareAndSetState

        // From STOPPED
        assertTrue(stateManager.compareAndSetState(PlaybackState.STOPPED, PlaybackState.PLAYING));
        stateManager.reset();
        assertFalse(stateManager.compareAndSetState(PlaybackState.STOPPED, PlaybackState.PAUSED));
        assertFalse(stateManager.compareAndSetState(PlaybackState.STOPPED, PlaybackState.FINISHED));

        // From PLAYING
        stateManager.transitionToPlaying();
        assertTrue(stateManager.compareAndSetState(PlaybackState.PLAYING, PlaybackState.PAUSED));
        stateManager.resume(); // Back to PLAYING
        assertTrue(stateManager.compareAndSetState(PlaybackState.PLAYING, PlaybackState.STOPPED));
        stateManager.transitionToPlaying();
        assertTrue(stateManager.compareAndSetState(PlaybackState.PLAYING, PlaybackState.FINISHED));

        // From PAUSED
        stateManager.transitionToPlaying(); // Start from FINISHED -> PLAYING
        stateManager.transitionToPaused();
        assertTrue(stateManager.compareAndSetState(PlaybackState.PAUSED, PlaybackState.PLAYING));
        stateManager.transitionToPaused();
        assertTrue(stateManager.compareAndSetState(PlaybackState.PAUSED, PlaybackState.STOPPED));
        stateManager.transitionToPlaying();
        stateManager.transitionToPaused();
        assertFalse(stateManager.compareAndSetState(PlaybackState.PAUSED, PlaybackState.FINISHED));

        // From FINISHED
        stateManager.reset();
        stateManager.transitionToPlaying();
        stateManager.transitionToFinished();
        assertTrue(stateManager.compareAndSetState(PlaybackState.FINISHED, PlaybackState.PLAYING));
        // Now in PLAYING state, go back to FINISHED to test transitions
        stateManager.transitionToFinished();
        // SHOULD be able to go from FINISHED to STOPPED (desired behavior)
        assertTrue(stateManager.compareAndSetState(PlaybackState.FINISHED, PlaybackState.STOPPED));

        // Reset to test invalid FINISHED transitions
        stateManager.transitionToPlaying();
        stateManager.transitionToFinished();
        assertFalse(stateManager.compareAndSetState(PlaybackState.FINISHED, PlaybackState.PAUSED));
        assertFalse(
                stateManager.compareAndSetState(PlaybackState.FINISHED, PlaybackState.FINISHED));
    }
}
