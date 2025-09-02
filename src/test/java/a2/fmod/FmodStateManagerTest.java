package a2.fmod;

import static org.junit.jupiter.api.Assertions.*;

import a2.exceptions.AudioEngineException;
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

class FmodStateManagerTest {

    private FmodStateManager stateManager;

    @BeforeEach
    void setUp() {
        stateManager = new FmodStateManager();
    }

    @Test
    void testInitialState() {
        assertEquals(FmodStateManager.State.UNINITIALIZED, stateManager.getCurrentState());
        assertFalse(stateManager.isRunning());
    }

    @Test
    void testValidTransitions() {
        // UNINITIALIZED -> INITIALIZING
        assertTrue(
                stateManager.compareAndSetState(
                        FmodStateManager.State.UNINITIALIZED, FmodStateManager.State.INITIALIZING));
        assertEquals(FmodStateManager.State.INITIALIZING, stateManager.getCurrentState());

        // INITIALIZING -> INITIALIZED
        assertTrue(
                stateManager.compareAndSetState(
                        FmodStateManager.State.INITIALIZING, FmodStateManager.State.INITIALIZED));
        assertEquals(FmodStateManager.State.INITIALIZED, stateManager.getCurrentState());
        assertTrue(stateManager.isRunning());

        // INITIALIZED -> CLOSING
        assertTrue(
                stateManager.compareAndSetState(
                        FmodStateManager.State.INITIALIZED, FmodStateManager.State.CLOSING));
        assertEquals(FmodStateManager.State.CLOSING, stateManager.getCurrentState());
        assertFalse(stateManager.isRunning());

        // CLOSING -> CLOSED
        assertTrue(
                stateManager.compareAndSetState(
                        FmodStateManager.State.CLOSING, FmodStateManager.State.CLOSED));
        assertEquals(FmodStateManager.State.CLOSED, stateManager.getCurrentState());

        // CLOSED -> INITIALIZING (re-initialization)
        assertTrue(
                stateManager.compareAndSetState(
                        FmodStateManager.State.CLOSED, FmodStateManager.State.INITIALIZING));
        assertEquals(FmodStateManager.State.INITIALIZING, stateManager.getCurrentState());
    }

    @Test
    void testInvalidTransitions() {
        // UNINITIALIZED -> INITIALIZED (skipping INITIALIZING)
        assertThrows(
                AudioEngineException.class,
                () -> stateManager.transitionTo(FmodStateManager.State.INITIALIZED, () -> {}));

        // UNINITIALIZED -> CLOSING
        assertThrows(
                AudioEngineException.class,
                () -> stateManager.transitionTo(FmodStateManager.State.CLOSING, () -> {}));

        // UNINITIALIZED -> CLOSED (no longer allowed)
        assertFalse(
                stateManager.compareAndSetState(
                        FmodStateManager.State.UNINITIALIZED, FmodStateManager.State.CLOSED));
    }

    @Test
    void testTransitionRollbackOnActionFailure() {
        // First test successful action execution
        AtomicInteger counter = new AtomicInteger(0);
        stateManager.transitionTo(
                FmodStateManager.State.INITIALIZING,
                () -> {
                    counter.incrementAndGet();
                });
        assertEquals(1, counter.get());
        assertEquals(FmodStateManager.State.INITIALIZING, stateManager.getCurrentState());

        // Now test rollback on exception
        assertThrows(
                RuntimeException.class,
                () ->
                        stateManager.transitionTo(
                                FmodStateManager.State.INITIALIZED,
                                () -> {
                                    throw new RuntimeException("Action failed");
                                }));

        // State should be rolled back to INITIALIZING
        assertEquals(FmodStateManager.State.INITIALIZING, stateManager.getCurrentState());
    }

    @Test
    void testExecuteInState() {
        // Move to INITIALIZED state
        stateManager.compareAndSetState(
                FmodStateManager.State.UNINITIALIZED, FmodStateManager.State.INITIALIZING);
        stateManager.compareAndSetState(
                FmodStateManager.State.INITIALIZING, FmodStateManager.State.INITIALIZED);

        // Execute action in correct state
        AtomicInteger result = new AtomicInteger();
        stateManager.executeInState(
                FmodStateManager.State.INITIALIZED,
                () -> {
                    result.set(42);
                });
        assertEquals(42, result.get());

        // Try to execute in wrong state
        assertThrows(
                AudioEngineException.class,
                () -> stateManager.executeInState(FmodStateManager.State.UNINITIALIZED, () -> {}));
    }

    @Test
    void testCheckStateAny() {
        stateManager.compareAndSetState(
                FmodStateManager.State.UNINITIALIZED, FmodStateManager.State.INITIALIZING);

        // Should pass - current state is one of the expected
        assertDoesNotThrow(
                () ->
                        stateManager.checkStateAny(
                                FmodStateManager.State.INITIALIZING,
                                FmodStateManager.State.INITIALIZED));

        // Should fail - current state is not in the list
        assertThrows(
                AudioEngineException.class,
                () ->
                        stateManager.checkStateAny(
                                FmodStateManager.State.CLOSED, FmodStateManager.State.CLOSING));
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
                                    FmodStateManager.State.UNINITIALIZED,
                                    FmodStateManager.State.INITIALIZING)) {
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
        assertEquals(FmodStateManager.State.INITIALIZING, stateManager.getCurrentState());

        executor.shutdown();
    }

    @Test
    void testExecuteWithLock() {
        AtomicReference<FmodStateManager.State> capturedState = new AtomicReference<>();

        FmodStateManager.State result =
                stateManager.executeWithLock(
                        () -> {
                            capturedState.set(stateManager.getCurrentState());
                            return stateManager.getCurrentState();
                        });

        assertEquals(FmodStateManager.State.UNINITIALIZED, result);
        assertEquals(FmodStateManager.State.UNINITIALIZED, capturedState.get());
    }

    @Test
    void testIsRunning() {
        assertFalse(stateManager.isRunning());

        stateManager.compareAndSetState(
                FmodStateManager.State.UNINITIALIZED, FmodStateManager.State.INITIALIZING);
        assertFalse(stateManager.isRunning());

        stateManager.compareAndSetState(
                FmodStateManager.State.INITIALIZING, FmodStateManager.State.INITIALIZED);
        assertTrue(stateManager.isRunning());

        stateManager.compareAndSetState(
                FmodStateManager.State.INITIALIZED, FmodStateManager.State.CLOSING);
        assertFalse(stateManager.isRunning());

        stateManager.compareAndSetState(
                FmodStateManager.State.CLOSING, FmodStateManager.State.CLOSED);
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
                FmodStateManager.State.UNINITIALIZED, FmodStateManager.State.INITIALIZING);
        stateManager.compareAndSetState(
                FmodStateManager.State.INITIALIZING, FmodStateManager.State.INITIALIZED);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(
                    () -> {
                        try {
                            startLatch.await();
                            for (int j = 0; j < operationsPerThread; j++) {
                                try {
                                    stateManager.executeInState(
                                            FmodStateManager.State.INITIALIZED,
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
                                    FmodStateManager.State.UNINITIALIZED,
                                    FmodStateManager.State.INITIALIZING);
                            stateManager.compareAndSetState(
                                    FmodStateManager.State.INITIALIZING,
                                    FmodStateManager.State.INITIALIZED);
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
                                        == FmodStateManager.State.INITIALIZED) {
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
                                    FmodStateManager.State.INITIALIZING,
                                    () -> {
                                        throw testException;
                                    });
                        });
        assertSame(testException, caught1);
        assertEquals(FmodStateManager.State.UNINITIALIZED, stateManager.getCurrentState());

        // Test executeInState
        RuntimeException caught2 =
                assertThrows(
                        RuntimeException.class,
                        () -> {
                            stateManager.executeInState(
                                    FmodStateManager.State.UNINITIALIZED,
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
        assertEquals(FmodStateManager.State.UNINITIALIZED, stateManager.getCurrentState());

        // Can reach INITIALIZING
        assertTrue(
                stateManager.compareAndSetState(
                        FmodStateManager.State.UNINITIALIZED, FmodStateManager.State.INITIALIZING));

        // Can reach INITIALIZED
        assertTrue(
                stateManager.compareAndSetState(
                        FmodStateManager.State.INITIALIZING, FmodStateManager.State.INITIALIZED));

        // Can reach CLOSING
        assertTrue(
                stateManager.compareAndSetState(
                        FmodStateManager.State.INITIALIZED, FmodStateManager.State.CLOSING));

        // Can reach CLOSED
        assertTrue(
                stateManager.compareAndSetState(
                        FmodStateManager.State.CLOSING, FmodStateManager.State.CLOSED));

        // CLOSED can restart the cycle (re-initialization)
        assertTrue(
                stateManager.compareAndSetState(
                        FmodStateManager.State.CLOSED, FmodStateManager.State.INITIALIZING));

        // Verify all states were reachable
        // (We've transitioned through all 5 states)
    }
}
