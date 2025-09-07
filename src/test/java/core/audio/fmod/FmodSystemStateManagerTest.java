package audio.fmod;

import static org.junit.jupiter.api.Assertions.*;

import audio.exceptions.AudioEngineException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class FmodSystemStateManagerTest {

    private FmodSystemStateManager stateManager;

    @BeforeEach
    void setUp() {
        stateManager = new FmodSystemStateManager();
    }

    @Test
    void testInitialState() {
        assertEquals(FmodSystemStateManager.State.UNINITIALIZED, stateManager.getCurrentState());
        assertFalse(stateManager.isRunning());
    }

    @Test
    void testValidTransitions() {
        // UNINITIALIZED -> INITIALIZING
        assertTrue(
                stateManager.compareAndSetState(
                        FmodSystemStateManager.State.UNINITIALIZED,
                        FmodSystemStateManager.State.INITIALIZING));
        assertEquals(FmodSystemStateManager.State.INITIALIZING, stateManager.getCurrentState());

        // INITIALIZING -> INITIALIZED
        assertTrue(
                stateManager.compareAndSetState(
                        FmodSystemStateManager.State.INITIALIZING,
                        FmodSystemStateManager.State.INITIALIZED));
        assertEquals(FmodSystemStateManager.State.INITIALIZED, stateManager.getCurrentState());
        assertTrue(stateManager.isRunning());

        // INITIALIZED -> CLOSING
        assertTrue(
                stateManager.compareAndSetState(
                        FmodSystemStateManager.State.INITIALIZED,
                        FmodSystemStateManager.State.CLOSING));
        assertEquals(FmodSystemStateManager.State.CLOSING, stateManager.getCurrentState());
        assertFalse(stateManager.isRunning());

        // CLOSING -> CLOSED
        assertTrue(
                stateManager.compareAndSetState(
                        FmodSystemStateManager.State.CLOSING, FmodSystemStateManager.State.CLOSED));
        assertEquals(FmodSystemStateManager.State.CLOSED, stateManager.getCurrentState());

        // CLOSED -> INITIALIZING (re-initialization)
        assertTrue(
                stateManager.compareAndSetState(
                        FmodSystemStateManager.State.CLOSED,
                        FmodSystemStateManager.State.INITIALIZING));
        assertEquals(FmodSystemStateManager.State.INITIALIZING, stateManager.getCurrentState());
    }

    @Test
    void testInvalidTransitions() {
        // UNINITIALIZED -> INITIALIZED (skipping INITIALIZING)
        assertThrows(
                AudioEngineException.class,
                () ->
                        stateManager.transitionTo(
                                FmodSystemStateManager.State.INITIALIZED, () -> {}));

        // UNINITIALIZED -> CLOSING
        assertThrows(
                AudioEngineException.class,
                () -> stateManager.transitionTo(FmodSystemStateManager.State.CLOSING, () -> {}));

        // UNINITIALIZED -> CLOSED (no longer allowed)
        assertFalse(
                stateManager.compareAndSetState(
                        FmodSystemStateManager.State.UNINITIALIZED,
                        FmodSystemStateManager.State.CLOSED));
    }

    @Test
    void testTransitionRollbackOnActionFailure() {
        // First test successful action execution
        AtomicInteger counter = new AtomicInteger(0);
        stateManager.transitionTo(
                FmodSystemStateManager.State.INITIALIZING,
                () -> {
                    counter.incrementAndGet();
                });
        assertEquals(1, counter.get());
        assertEquals(FmodSystemStateManager.State.INITIALIZING, stateManager.getCurrentState());

        // Now test rollback on exception
        assertThrows(
                RuntimeException.class,
                () ->
                        stateManager.transitionTo(
                                FmodSystemStateManager.State.INITIALIZED,
                                () -> {
                                    throw new RuntimeException("Action failed");
                                }));

        // State should be rolled back to INITIALIZING
        assertEquals(FmodSystemStateManager.State.INITIALIZING, stateManager.getCurrentState());
    }

    @Test
    void testExecuteInState() {
        // Move to INITIALIZED state
        stateManager.compareAndSetState(
                FmodSystemStateManager.State.UNINITIALIZED,
                FmodSystemStateManager.State.INITIALIZING);
        stateManager.compareAndSetState(
                FmodSystemStateManager.State.INITIALIZING,
                FmodSystemStateManager.State.INITIALIZED);

        // Execute action in correct state
        AtomicInteger result = new AtomicInteger();
        stateManager.executeInState(
                FmodSystemStateManager.State.INITIALIZED,
                () -> {
                    result.set(42);
                });
        assertEquals(42, result.get());

        // Try to execute in wrong state
        assertThrows(
                AudioEngineException.class,
                () ->
                        stateManager.executeInState(
                                FmodSystemStateManager.State.UNINITIALIZED, () -> {}));
    }

    @Test
    void testCheckStateAny() {
        stateManager.compareAndSetState(
                FmodSystemStateManager.State.UNINITIALIZED,
                FmodSystemStateManager.State.INITIALIZING);

        // Should pass - current state is one of the expected
        assertDoesNotThrow(
                () ->
                        stateManager.checkStateAny(
                                FmodSystemStateManager.State.INITIALIZING,
                                FmodSystemStateManager.State.INITIALIZED));

        // Should fail - current state is not in the list
        assertThrows(
                AudioEngineException.class,
                () ->
                        stateManager.checkStateAny(
                                FmodSystemStateManager.State.CLOSED,
                                FmodSystemStateManager.State.CLOSING));
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
                                    FmodSystemStateManager.State.UNINITIALIZED,
                                    FmodSystemStateManager.State.INITIALIZING)) {
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
        assertEquals(FmodSystemStateManager.State.INITIALIZING, stateManager.getCurrentState());

        executor.shutdown();
    }

    @Test
    void testExecuteWithLock() {
        AtomicReference<FmodSystemStateManager.State> capturedState = new AtomicReference<>();

        FmodSystemStateManager.State result =
                stateManager.executeWithLock(
                        () -> {
                            capturedState.set(stateManager.getCurrentState());
                            return stateManager.getCurrentState();
                        });

        assertEquals(FmodSystemStateManager.State.UNINITIALIZED, result);
        assertEquals(FmodSystemStateManager.State.UNINITIALIZED, capturedState.get());
    }

    @Test
    void testIsRunning() {
        assertFalse(stateManager.isRunning());

        stateManager.compareAndSetState(
                FmodSystemStateManager.State.UNINITIALIZED,
                FmodSystemStateManager.State.INITIALIZING);
        assertFalse(stateManager.isRunning());

        stateManager.compareAndSetState(
                FmodSystemStateManager.State.INITIALIZING,
                FmodSystemStateManager.State.INITIALIZED);
        assertTrue(stateManager.isRunning());

        stateManager.compareAndSetState(
                FmodSystemStateManager.State.INITIALIZED, FmodSystemStateManager.State.CLOSING);
        assertFalse(stateManager.isRunning());

        stateManager.compareAndSetState(
                FmodSystemStateManager.State.CLOSING, FmodSystemStateManager.State.CLOSED);
        assertFalse(stateManager.isRunning());
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

        // First, get to INITIALIZED state
        stateManager.compareAndSetState(
                FmodSystemStateManager.State.UNINITIALIZED,
                FmodSystemStateManager.State.INITIALIZING);
        stateManager.compareAndSetState(
                FmodSystemStateManager.State.INITIALIZING,
                FmodSystemStateManager.State.INITIALIZED);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(
                    () -> {
                        try {
                            startLatch.await();
                            for (int j = 0; j < operationsPerThread; j++) {
                                try {
                                    stateManager.executeInState(
                                            FmodSystemStateManager.State.INITIALIZED,
                                            () -> {
                                                // Simulate some work
                                                successfulOps.incrementAndGet();
                                            });
                                } catch (AudioEngineException e) {
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
        // Transition to INITIALIZED in one thread
        Thread writer =
                new Thread(
                        () -> {
                            stateManager.compareAndSetState(
                                    FmodSystemStateManager.State.UNINITIALIZED,
                                    FmodSystemStateManager.State.INITIALIZING);
                            stateManager.compareAndSetState(
                                    FmodSystemStateManager.State.INITIALIZING,
                                    FmodSystemStateManager.State.INITIALIZED);
                        });

        writer.start();
        writer.join();

        // Read in multiple threads - should all see INITIALIZED
        int readerCount = 10;
        CountDownLatch latch = new CountDownLatch(readerCount);
        AtomicInteger correctReads = new AtomicInteger(0);

        for (int i = 0; i < readerCount; i++) {
            new Thread(
                            () -> {
                                if (stateManager.getCurrentState()
                                        == FmodSystemStateManager.State.INITIALIZED) {
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
    void testExceptionPropagationInDifferentMethods() {
        RuntimeException testException = new RuntimeException("Test exception");

        // Test transitionTo
        RuntimeException caught1 =
                assertThrows(
                        RuntimeException.class,
                        () -> {
                            stateManager.transitionTo(
                                    FmodSystemStateManager.State.INITIALIZING,
                                    () -> {
                                        throw testException;
                                    });
                        });
        assertSame(testException, caught1);
        assertEquals(FmodSystemStateManager.State.UNINITIALIZED, stateManager.getCurrentState());

        // Test executeInState
        RuntimeException caught2 =
                assertThrows(
                        RuntimeException.class,
                        () -> {
                            stateManager.executeInState(
                                    FmodSystemStateManager.State.UNINITIALIZED,
                                    () -> {
                                        throw testException;
                                    });
                        });
        assertSame(testException, caught2);

        // Test executeWithLock
        RuntimeException caught3 =
                assertThrows(
                        RuntimeException.class,
                        () -> {
                            stateManager.executeWithLock(
                                    () -> {
                                        throw testException;
                                    });
                        });
        assertSame(testException, caught3);
    }

    @Test
    void testAllStatesReachableAndTerminal() {
        // Verify we can reach every state and CLOSED is terminal except for re-initialization

        // Start at UNINITIALIZED
        assertEquals(FmodSystemStateManager.State.UNINITIALIZED, stateManager.getCurrentState());

        // Can reach INITIALIZING
        assertTrue(
                stateManager.compareAndSetState(
                        FmodSystemStateManager.State.UNINITIALIZED,
                        FmodSystemStateManager.State.INITIALIZING));

        // Can reach INITIALIZED
        assertTrue(
                stateManager.compareAndSetState(
                        FmodSystemStateManager.State.INITIALIZING,
                        FmodSystemStateManager.State.INITIALIZED));

        // Can reach CLOSING
        assertTrue(
                stateManager.compareAndSetState(
                        FmodSystemStateManager.State.INITIALIZED,
                        FmodSystemStateManager.State.CLOSING));

        // Can reach CLOSED
        assertTrue(
                stateManager.compareAndSetState(
                        FmodSystemStateManager.State.CLOSING, FmodSystemStateManager.State.CLOSED));

        // CLOSED can restart the cycle (re-initialization)
        assertTrue(
                stateManager.compareAndSetState(
                        FmodSystemStateManager.State.CLOSED,
                        FmodSystemStateManager.State.INITIALIZING));

        // Verify all states were reachable
        // (We've transitioned through all 5 states)
    }
}
