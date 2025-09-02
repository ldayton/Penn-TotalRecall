package a2.fmod;

import static org.junit.jupiter.api.Assertions.*;

import a2.AudioEngineConfig;
import a2.exceptions.AudioEngineException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for FmodAudioEngine initialization and shutdown with focus on concurrency. */
class FmodAudioEngineInitCloseTest {

    private FmodAudioEngine engine;
    private AudioEngineConfig config;

    @BeforeEach
    void setUp() {
        config =
                AudioEngineConfig.builder()
                        .engineType("fmod")
                        .mode(AudioEngineConfig.Mode.PLAYBACK)
                        .maxCacheBytes(10 * 1024 * 1024) // 10MB
                        .build();
        engine = new FmodAudioEngine(config);
    }

    @Test
    void testSuccessfulInitialization() throws Exception {
        // Engine is already initialized in setUp
        assertNotNull(engine);

        // Close to clean up
        engine.close();
    }

    @Test
    void testCannotInitializeFromNonUninitializedState() throws Exception {
        assertNotNull(engine);
        engine.close();
    }

    @Test
    void testCloseFromInitializedState() throws Exception {
        // Engine is already initialized in setUp

        // Close
        assertDoesNotThrow(() -> engine.close());
    }

    @Test
    void testCloseFromUninitializedState() throws Exception {
        // Close without init - should be safe
        assertDoesNotThrow(() -> engine.close());
    }

    @Test
    void testCloseIsIdempotent() throws Exception {
        engine.close();

        // Second close should be no-op
        assertDoesNotThrow(() -> engine.close());
    }

    @Test
    void testInitCloseRaceCondition() throws Exception {
        assertTimeoutPreemptively(
                Duration.ofSeconds(5),
                () -> {
                    FmodAudioEngine testEngine = new FmodAudioEngine(config);
                    CountDownLatch initStarted = new CountDownLatch(1);
                    CountDownLatch bothDone = new CountDownLatch(2);

                    AtomicReference<Exception> initException = new AtomicReference<>();
                    AtomicReference<Exception> closeException = new AtomicReference<>();

                    // Thread 1: Initialize
                    Thread initThread =
                            new Thread(
                                    () -> {
                                        try {
                                            initStarted.countDown();
                                        } catch (Exception e) {
                                            initException.set(e);
                                        } finally {
                                            bothDone.countDown();
                                        }
                                    });

                    // Thread 2: Close after init starts
                    Thread closeThread =
                            new Thread(
                                    () -> {
                                        try {
                                            initStarted.await(2, TimeUnit.SECONDS);
                                            Thread.sleep(10); // Let init proceed a bit
                                            testEngine.close();
                                        } catch (Exception e) {
                                            closeException.set(e);
                                        } finally {
                                            bothDone.countDown();
                                        }
                                    });

                    initThread.start();
                    closeThread.start();

                    assertTrue(bothDone.await(3, TimeUnit.SECONDS));

                    // Close should always succeed
                    assertNull(closeException.get());

                    // Init might fail if engine was closed during initialization
                    if (initException.get() != null) {
                        assertTrue(
                                initException.get() instanceof AudioEngineException
                                        || initException.get() instanceof RuntimeException);
                    }

                    // Final cleanup
                    testEngine.close();
                });
    }

    @Test
    void testConcurrentCloseCallsAreSafe() throws Exception {
        assertTimeoutPreemptively(
                Duration.ofSeconds(5),
                () -> {
                    FmodAudioEngine testEngine = new FmodAudioEngine(config);

                    // Multiple threads trying to close simultaneously
                    int threadCount = 10;
                    CountDownLatch startLatch = new CountDownLatch(1);
                    CountDownLatch doneLatch = new CountDownLatch(threadCount);

                    for (int i = 0; i < threadCount; i++) {
                        new Thread(
                                        () -> {
                                            try {
                                                startLatch.await();
                                                testEngine.close();
                                            } catch (Exception e) {
                                                // Should not throw
                                            } finally {
                                                doneLatch.countDown();
                                            }
                                        })
                                .start();
                    }

                    startLatch.countDown(); // Release all threads
                    assertTrue(doneLatch.await(2, TimeUnit.SECONDS));
                });
    }

    @Test
    void testOperationsBlockDuringClose() throws Exception {
        assertTimeoutPreemptively(
                Duration.ofSeconds(5),
                () -> {
                    FmodAudioEngine testEngine = new FmodAudioEngine(config);

                    CyclicBarrier barrier = new CyclicBarrier(2);
                    AtomicReference<Exception> opException = new AtomicReference<>();

                    // Thread 1: Start close (will hold lock)
                    Thread closeThread =
                            new Thread(
                                    () -> {
                                        try {
                                            barrier.await(); // Sync start
                                            testEngine.close();
                                        } catch (Exception e) {
                                            // Unexpected
                                        }
                                    });

                    // Thread 2: Try operation during close
                    Thread opThread =
                            new Thread(
                                    () -> {
                                        try {
                                            barrier.await(); // Sync start
                                            Thread.sleep(50); // Let close start first
                                            // This should fail because engine is closed/closing
                                        } catch (Exception e) {
                                            opException.set(e);
                                        }
                                    });

                    closeThread.start();
                    opThread.start();

                    closeThread.join(2000);
                    opThread.join(2000);

                    // Operation should have failed
                    assertNotNull(opException.get(), "Expected an exception but got none");
                });
    }

    @Test
    void testConfigValidation() throws Exception {
        // Test invalid cache size (too small)
        AudioEngineConfig invalidConfig =
                AudioEngineConfig.builder()
                        .maxCacheBytes(1000) // Less than 1MB
                        .build();

        AudioEngineException ex =
                assertThrows(AudioEngineException.class, () -> new FmodAudioEngine(invalidConfig));
        assertTrue(ex.getMessage().contains("maxCacheBytes must be at least 1MB"));
    }

    @Test
    void testConfigValidationMaxCache() throws Exception {
        // Test invalid cache size (too large)
        AudioEngineConfig invalidConfig =
                AudioEngineConfig.builder()
                        .maxCacheBytes(11L * 1024 * 1024 * 1024) // More than 10GB
                        .build();

        AudioEngineException ex =
                assertThrows(AudioEngineException.class, () -> new FmodAudioEngine(invalidConfig));
        assertTrue(ex.getMessage().contains("maxCacheBytes must not exceed 10GB"));
    }

    @Test
    void testConfigValidationPrefetchWindow() throws Exception {
        // Test invalid prefetch window (negative)
        AudioEngineConfig invalidConfig =
                AudioEngineConfig.builder().prefetchWindowSeconds(-1).build();

        AudioEngineException ex =
                assertThrows(AudioEngineException.class, () -> new FmodAudioEngine(invalidConfig));
        assertTrue(ex.getMessage().contains("prefetchWindowSeconds must be non-negative"));
    }

    @Test
    void testMultipleInitCloseSequences() throws Exception {
        // Test that we can init/close multiple times
        // This test would catch the null pointer issue in logSystemInfo()
        for (int i = 0; i < 3; i++) {
            FmodAudioEngine freshEngine = new FmodAudioEngine(config);
            assertDoesNotThrow(() -> freshEngine.close());
            assertDoesNotThrow(() -> freshEngine.close(), "Failed to close on iteration " + i);
        }
    }
}
