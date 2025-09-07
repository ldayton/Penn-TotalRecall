package core.audio.fmod;

import static org.junit.jupiter.api.Assertions.*;

import annotations.Audio;
import core.audio.exceptions.AudioEngineException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Integration tests for FmodSystemManager. Tests the FMOD system lifecycle including
 * initialization, configuration, and shutdown.
 */
@Audio
class FmodSystemManagerTest {

    private FmodSystemManager manager;

    @BeforeEach
    void setUp() {
        manager = new FmodSystemManager();
    }

    @AfterEach
    void tearDown() {
        if (manager != null && manager.isInitialized()) {
            manager.shutdown();
        }
    }

    @Test
    @DisplayName("Initial state should be uninitialized")
    void testInitialState() {
        assertFalse(manager.isInitialized());
        assertNull(manager.getFmodLibrary());
        assertNull(manager.getSystem());
        assertEquals("", manager.getVersionInfo());
        assertEquals("", manager.getBufferInfo());
        assertEquals("", manager.getFormatInfo());
    }

    @Test
    @DisplayName("Should initialize FMOD system successfully")
    void testInitialization() {
        assertDoesNotThrow(() -> manager.initialize());
        assertTrue(manager.isInitialized());
        assertNotNull(manager.getFmodLibrary());
        assertNotNull(manager.getSystem());
    }

    @Test
    @DisplayName("Should throw exception when already initialized")
    void testDoubleInitialization() {
        assertDoesNotThrow(() -> manager.initialize());

        AudioEngineException exception =
                assertThrows(AudioEngineException.class, () -> manager.initialize());
        assertTrue(exception.getMessage().contains("already initialized"));
    }

    @Test
    @DisplayName("Should get version info when initialized")
    void testGetVersionInfo() {
        manager.initialize();

        String versionInfo = manager.getVersionInfo();
        assertFalse(versionInfo.isEmpty());
        assertTrue(versionInfo.matches("\\d+\\.\\d+\\.\\d+ \\(build \\d+\\)"));
    }

    @Test
    @DisplayName("Should get buffer info when initialized")
    void testGetBufferInfo() {
        manager.initialize();

        String bufferInfo = manager.getBufferInfo();
        assertFalse(bufferInfo.isEmpty());
        assertTrue(bufferInfo.contains("samples"));
        assertTrue(bufferInfo.contains("buffers"));
    }

    @Test
    @DisplayName("Should get format info when initialized")
    void testGetFormatInfo() {
        manager.initialize();

        String formatInfo = manager.getFormatInfo();
        assertFalse(formatInfo.isEmpty());
        assertTrue(formatInfo.contains("Hz"));
        assertTrue(formatInfo.contains("speaker mode"));
    }

    @Test
    @DisplayName("Should configure for low latency playback")
    void testLowLatencyConfiguration() {
        manager.initialize();

        String bufferInfo = manager.getBufferInfo();
        // Should have small buffer size for low latency
        assertTrue(bufferInfo.contains("256") || bufferInfo.contains("512"));

        String formatInfo = manager.getFormatInfo();
        // Should be configured for mono at 48kHz as per configureForPlayback
        assertTrue(formatInfo.contains("48000") || formatInfo.contains("44100"));
    }

    @Test
    @DisplayName("Update should work when initialized")
    void testUpdate() {
        manager.initialize();
        assertDoesNotThrow(() -> manager.update());
    }

    @Test
    @DisplayName("Update should be safe when not initialized")
    void testUpdateWhenNotInitialized() {
        assertDoesNotThrow(() -> manager.update());
    }

    @Test
    @DisplayName("Should shutdown cleanly")
    void testShutdown() {
        manager.initialize();
        assertTrue(manager.isInitialized());

        manager.shutdown();

        assertFalse(manager.isInitialized());
        assertNull(manager.getFmodLibrary());
        assertNull(manager.getSystem());
    }

    @Test
    @DisplayName("Shutdown should be idempotent")
    void testMultipleShutdowns() {
        manager.initialize();

        manager.shutdown();
        assertFalse(manager.isInitialized());

        // Second shutdown should not throw
        assertDoesNotThrow(() -> manager.shutdown());
        assertFalse(manager.isInitialized());
    }

    @Test
    @DisplayName("Shutdown when not initialized should be safe")
    void testShutdownWhenNotInitialized() {
        assertDoesNotThrow(() -> manager.shutdown());
        assertFalse(manager.isInitialized());
    }

    @Test
    @DisplayName("Should support re-initialization after shutdown")
    void testReinitializationAfterShutdown() {
        // First initialization
        manager.initialize();
        assertTrue(manager.isInitialized());
        String version1 = manager.getVersionInfo();

        // Shutdown
        manager.shutdown();
        assertFalse(manager.isInitialized());

        // Re-initialize
        assertDoesNotThrow(() -> manager.initialize());
        assertTrue(manager.isInitialized());
        String version2 = manager.getVersionInfo();

        // Should get same version
        assertEquals(version1, version2);
    }

    @Test
    @DisplayName("Info methods should return empty when not initialized")
    void testInfoMethodsWhenNotInitialized() {
        assertEquals("", manager.getVersionInfo());
        assertEquals("", manager.getBufferInfo());
        assertEquals("", manager.getFormatInfo());
    }

    @Test
    @Timeout(5)
    @DisplayName("Should handle concurrent initialization attempts safely")
    void testConcurrentInitialization() throws InterruptedException {
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger alreadyInitializedCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(
                    () -> {
                        try {
                            startLatch.await();
                            manager.initialize();
                            successCount.incrementAndGet();
                        } catch (AudioEngineException e) {
                            if (e.getMessage().contains("already initialized")) {
                                alreadyInitializedCount.incrementAndGet();
                            }
                        } catch (Exception e) {
                            // Other exceptions
                        } finally {
                            doneLatch.countDown();
                        }
                    });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(3, TimeUnit.SECONDS));

        // Exactly one thread should succeed
        assertEquals(1, successCount.get());
        // Others should get "already initialized" error
        assertEquals(threadCount - 1, alreadyInitializedCount.get());

        assertTrue(manager.isInitialized());

        executor.shutdown();
    }

    @Test
    @Timeout(5)
    @DisplayName("Should handle concurrent shutdown safely")
    void testConcurrentShutdown() throws InterruptedException {
        manager.initialize();

        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(
                    () -> {
                        try {
                            startLatch.await();
                            manager.shutdown();
                        } catch (Exception e) {
                            // Should not throw
                        } finally {
                            doneLatch.countDown();
                        }
                    });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(3, TimeUnit.SECONDS));

        // Should be shut down
        assertFalse(manager.isInitialized());

        executor.shutdown();
    }

    @Test
    @Timeout(5)
    @DisplayName("Should handle concurrent updates safely")
    void testConcurrentUpdates() throws InterruptedException {
        manager.initialize();

        int threadCount = 10;
        int updatesPerThread = 100;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger updateCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(
                    () -> {
                        try {
                            startLatch.await();
                            for (int j = 0; j < updatesPerThread; j++) {
                                manager.update();
                                updateCount.incrementAndGet();
                            }
                        } catch (Exception e) {
                            // Should not throw
                        } finally {
                            doneLatch.countDown();
                        }
                    });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(3, TimeUnit.SECONDS));

        assertEquals(threadCount * updatesPerThread, updateCount.get());

        executor.shutdown();
    }

    @Test
    @Timeout(5)
    @DisplayName("Should handle rapid init-shutdown cycles")
    void testRapidInitShutdownCycles() {
        for (int i = 0; i < 10; i++) {
            assertDoesNotThrow(() -> manager.initialize());
            assertTrue(manager.isInitialized());

            manager.shutdown();
            assertFalse(manager.isInitialized());
        }
    }

    @Test
    @DisplayName("Should handle operations in mixed order safely")
    void testMixedOperations() {
        // Update before init
        assertDoesNotThrow(() -> manager.update());

        // Get info before init
        assertEquals("", manager.getVersionInfo());

        // Initialize
        manager.initialize();
        assertTrue(manager.isInitialized());

        // Multiple updates
        for (int i = 0; i < 10; i++) {
            assertDoesNotThrow(() -> manager.update());
        }

        // Get info while initialized
        assertFalse(manager.getVersionInfo().isEmpty());

        // Shutdown
        manager.shutdown();

        // Operations after shutdown
        assertDoesNotThrow(() -> manager.update());
        assertEquals("", manager.getVersionInfo());
    }
}
