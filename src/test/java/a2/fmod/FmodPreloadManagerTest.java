package a2.fmod;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration tests for FmodPreloadManager focusing on cache management, memory tracking, async
 * operations, and thread safety.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FmodPreloadManagerTest {

    private FmodLibrary fmod;
    private Pointer system;
    private FmodPreloadManager preloadManager;

    @TempDir Path tempDir;

    // Test audio files
    private static final String SAMPLE_WAV = "packaging/samples/sample.wav";
    private static final String SWEEP_WAV = "packaging/samples/sweep.wav";

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
    }

    @BeforeEach
    void createFreshManager() {
        if (preloadManager != null) {
            preloadManager.shutdown();
        }
        // Small cache size for easier testing of eviction
        preloadManager = new FmodPreloadManager(fmod, system, 3);
    }

    @AfterAll
    void tearDownFmod() {
        if (preloadManager != null) {
            preloadManager.shutdown();
        }
        if (system != null && fmod != null) {
            fmod.FMOD_System_Release(system);
        }
    }

    // ========== Core Preloading Functionality Tests ==========

    @Test
    void testPreloadAndRetrieve() throws Exception {
        // Preload a range
        CompletableFuture<Void> future = preloadManager.preloadRange(SAMPLE_WAV, 1000, 2000);

        // Wait for async completion
        assertDoesNotThrow(() -> future.get(2, TimeUnit.SECONDS));

        // Verify it's cached
        assertTrue(preloadManager.isRangeCached(SAMPLE_WAV, 1000, 2000));

        // Retrieve the sound
        Optional<Pointer> sound = preloadManager.getPreloadedSound(SAMPLE_WAV, 1000, 2000);
        assertTrue(sound.isPresent());
        assertNotNull(sound.get());
    }

    @Test
    void testPreloadDeduplication() throws Exception {
        // First preload
        CompletableFuture<Void> future1 = preloadManager.preloadRange(SAMPLE_WAV, 1000, 2000);
        future1.get(2, TimeUnit.SECONDS);

        // Second preload of same range - should return immediately
        long startTime = System.currentTimeMillis();
        CompletableFuture<Void> future2 = preloadManager.preloadRange(SAMPLE_WAV, 1000, 2000);
        future2.get(100, TimeUnit.MILLISECONDS); // Should be instant
        long elapsed = System.currentTimeMillis() - startTime;

        // Should complete very quickly since it's already cached
        assertTrue(elapsed < 50, "Cached preload took too long: " + elapsed + "ms");

        // Verify still cached (only one copy)
        FmodPreloadManager.CacheStats stats = preloadManager.getCacheStats();
        assertEquals(1, stats.currentSize());
    }

    @Test
    void testDifferentRangesSameFile() throws Exception {
        // Preload different ranges from same file
        CompletableFuture<Void> future1 = preloadManager.preloadRange(SAMPLE_WAV, 1000, 2000);
        CompletableFuture<Void> future2 = preloadManager.preloadRange(SAMPLE_WAV, 2000, 3000);
        CompletableFuture<Void> future3 = preloadManager.preloadRange(SAMPLE_WAV, 3000, 4000);

        // Wait for all
        CompletableFuture.allOf(future1, future2, future3).get(3, TimeUnit.SECONDS);

        // All should be cached separately
        assertTrue(preloadManager.isRangeCached(SAMPLE_WAV, 1000, 2000));
        assertTrue(preloadManager.isRangeCached(SAMPLE_WAV, 2000, 3000));
        assertTrue(preloadManager.isRangeCached(SAMPLE_WAV, 3000, 4000));

        // Cache should have 3 entries
        FmodPreloadManager.CacheStats stats = preloadManager.getCacheStats();
        assertEquals(3, stats.currentSize());
    }

    @Test
    void testPreloadNonExistentFile() throws Exception {
        String fakePath = "/does/not/exist/audio.wav";

        // Should not throw exception (silent failure)
        CompletableFuture<Void> future = preloadManager.preloadRange(fakePath, 1000, 2000);
        assertDoesNotThrow(() -> future.get(2, TimeUnit.SECONDS));

        // Should not be cached
        assertFalse(preloadManager.isRangeCached(fakePath, 1000, 2000));
        Optional<Pointer> sound = preloadManager.getPreloadedSound(fakePath, 1000, 2000);
        assertFalse(sound.isPresent());
    }

    // ========== LRU Cache Behavior Tests ==========

    @Test
    void testLRUEviction() throws Exception {
        // Fill cache to capacity (size = 3)
        CompletableFuture<Void> f1 = preloadManager.preloadRange(SAMPLE_WAV, 1000, 2000);
        CompletableFuture<Void> f2 = preloadManager.preloadRange(SAMPLE_WAV, 2000, 3000);
        CompletableFuture<Void> f3 = preloadManager.preloadRange(SAMPLE_WAV, 3000, 4000);
        CompletableFuture.allOf(f1, f2, f3).get(3, TimeUnit.SECONDS);

        // Verify all cached
        assertEquals(3, preloadManager.getCacheStats().currentSize());
        assertTrue(preloadManager.isRangeCached(SAMPLE_WAV, 1000, 2000));

        // Add one more - should evict the oldest (1000-2000)
        CompletableFuture<Void> f4 = preloadManager.preloadRange(SAMPLE_WAV, 4000, 5000);
        f4.get(2, TimeUnit.SECONDS);

        // First should be evicted, others remain
        assertFalse(preloadManager.isRangeCached(SAMPLE_WAV, 1000, 2000));
        assertTrue(preloadManager.isRangeCached(SAMPLE_WAV, 2000, 3000));
        assertTrue(preloadManager.isRangeCached(SAMPLE_WAV, 3000, 4000));
        assertTrue(preloadManager.isRangeCached(SAMPLE_WAV, 4000, 5000));

        // Cache still at capacity
        assertEquals(3, preloadManager.getCacheStats().currentSize());
    }

    @Test
    void testCacheAccessUpdatesLRU() throws Exception {
        // Fill cache
        CompletableFuture<Void> f1 = preloadManager.preloadRange(SAMPLE_WAV, 1000, 2000);
        CompletableFuture<Void> f2 = preloadManager.preloadRange(SAMPLE_WAV, 2000, 3000);
        CompletableFuture<Void> f3 = preloadManager.preloadRange(SAMPLE_WAV, 3000, 4000);
        CompletableFuture.allOf(f1, f2, f3).get(3, TimeUnit.SECONDS);

        // Access the first entry to make it recently used
        Optional<Pointer> sound = preloadManager.getPreloadedSound(SAMPLE_WAV, 1000, 2000);
        assertTrue(sound.isPresent());

        // Add new entry - should evict 2000-3000 (now oldest), not 1000-2000
        CompletableFuture<Void> f4 = preloadManager.preloadRange(SAMPLE_WAV, 4000, 5000);
        f4.get(2, TimeUnit.SECONDS);

        // First still cached due to recent access
        assertTrue(preloadManager.isRangeCached(SAMPLE_WAV, 1000, 2000));
        assertFalse(preloadManager.isRangeCached(SAMPLE_WAV, 2000, 3000)); // Evicted
        assertTrue(preloadManager.isRangeCached(SAMPLE_WAV, 3000, 4000));
        assertTrue(preloadManager.isRangeCached(SAMPLE_WAV, 4000, 5000));
    }

    // ========== Memory Management Tests ==========

    @Test
    void testMemoryTracking() throws Exception {
        FmodPreloadManager.CacheStats initialStats = preloadManager.getCacheStats();
        assertEquals(0, initialStats.estimatedMemoryBytes());

        // Preload a range
        CompletableFuture<Void> future = preloadManager.preloadRange(SAMPLE_WAV, 1000, 2000);
        future.get(2, TimeUnit.SECONDS);

        // Memory should increase
        FmodPreloadManager.CacheStats afterStats = preloadManager.getCacheStats();
        assertTrue(afterStats.estimatedMemoryBytes() > 0);

        // Preload another range
        CompletableFuture<Void> future2 = preloadManager.preloadRange(SAMPLE_WAV, 2000, 3000);
        future2.get(2, TimeUnit.SECONDS);

        // Memory should increase further
        FmodPreloadManager.CacheStats finalStats = preloadManager.getCacheStats();
        assertTrue(finalStats.estimatedMemoryBytes() > afterStats.estimatedMemoryBytes());
    }

    @Test
    void testMemoryReleasedOnEviction() throws Exception {
        // Fill cache
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            futures.add(preloadManager.preloadRange(SAMPLE_WAV, i * 1000, (i + 1) * 1000));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(3, TimeUnit.SECONDS);

        long memoryBeforeEviction = preloadManager.getCacheStats().estimatedMemoryBytes();

        // Trigger eviction
        preloadManager.preloadRange(SAMPLE_WAV, 4000, 5000).get(2, TimeUnit.SECONDS);

        // Memory should stay roughly the same (one removed, one added)
        // Both sounds are from same file so should be similar size
        long memoryAfterEviction = preloadManager.getCacheStats().estimatedMemoryBytes();

        // Allow some variance but should be in same ballpark
        double ratio = (double) memoryAfterEviction / memoryBeforeEviction;
        assertTrue(
                ratio > 0.8 && ratio < 1.2,
                "Memory changed too much on eviction: "
                        + memoryBeforeEviction
                        + " -> "
                        + memoryAfterEviction);
    }

    @Test
    void testMemoryReleasedOnClear() throws Exception {
        // Load some sounds
        CompletableFuture<Void> f1 = preloadManager.preloadRange(SAMPLE_WAV, 1000, 2000);
        CompletableFuture<Void> f2 = preloadManager.preloadRange(SWEEP_WAV, 1000, 2000);
        CompletableFuture.allOf(f1, f2).get(3, TimeUnit.SECONDS);

        assertTrue(preloadManager.getCacheStats().estimatedMemoryBytes() > 0);

        // Clear all
        preloadManager.clearCache();

        // Memory should be zero
        assertEquals(0, preloadManager.getCacheStats().estimatedMemoryBytes());
        assertEquals(0, preloadManager.getCacheStats().currentSize());
    }

    // ========== Async Operation Tests ==========

    @Test
    void testAsyncPreloadCompletion() throws Exception {
        CompletableFuture<Void> future = preloadManager.preloadRange(SAMPLE_WAV, 1000, 2000);

        // Should not be cached immediately
        assertFalse(preloadManager.isRangeCached(SAMPLE_WAV, 1000, 2000));

        // Wait for completion
        future.get(2, TimeUnit.SECONDS);

        // Now should be cached
        assertTrue(preloadManager.isRangeCached(SAMPLE_WAV, 1000, 2000));
    }

    @Test
    void testMultipleAsyncPreloads() throws Exception {
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // Queue up multiple preloads
        for (int i = 0; i < 10; i++) {
            futures.add(preloadManager.preloadRange(SAMPLE_WAV, i * 1000, (i + 1) * 1000));
        }

        // Wait for all to complete
        CompletableFuture<Void> allFutures =
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        allFutures.get(5, TimeUnit.SECONDS);

        // Only last 3 should be cached (cache size = 3)
        FmodPreloadManager.CacheStats stats = preloadManager.getCacheStats();
        assertEquals(3, stats.currentSize());

        // Check that recent ones are cached
        assertTrue(preloadManager.isRangeCached(SAMPLE_WAV, 9000, 10000));
        assertTrue(preloadManager.isRangeCached(SAMPLE_WAV, 8000, 9000));
        assertTrue(preloadManager.isRangeCached(SAMPLE_WAV, 7000, 8000));
    }

    // ========== Cache Management Operations Tests ==========

    @Test
    void testClearFile() throws Exception {
        // Load from two different files
        CompletableFuture<Void> f1 = preloadManager.preloadRange(SAMPLE_WAV, 1000, 2000);
        CompletableFuture<Void> f2 = preloadManager.preloadRange(SAMPLE_WAV, 2000, 3000);
        CompletableFuture<Void> f3 = preloadManager.preloadRange(SWEEP_WAV, 1000, 2000);
        CompletableFuture.allOf(f1, f2, f3).get(3, TimeUnit.SECONDS);

        assertEquals(3, preloadManager.getCacheStats().currentSize());

        // Clear only SAMPLE_WAV
        preloadManager.clearFile(SAMPLE_WAV);

        // SAMPLE_WAV ranges should be gone
        assertFalse(preloadManager.isRangeCached(SAMPLE_WAV, 1000, 2000));
        assertFalse(preloadManager.isRangeCached(SAMPLE_WAV, 2000, 3000));

        // SWEEP_WAV should remain
        assertTrue(preloadManager.isRangeCached(SWEEP_WAV, 1000, 2000));
        assertEquals(1, preloadManager.getCacheStats().currentSize());
    }

    @Test
    void testClearCache() throws Exception {
        // Load multiple ranges
        CompletableFuture<Void> f1 = preloadManager.preloadRange(SAMPLE_WAV, 1000, 2000);
        CompletableFuture<Void> f2 = preloadManager.preloadRange(SAMPLE_WAV, 2000, 3000);
        CompletableFuture<Void> f3 = preloadManager.preloadRange(SWEEP_WAV, 1000, 2000);
        CompletableFuture.allOf(f1, f2, f3).get(3, TimeUnit.SECONDS);

        assertEquals(3, preloadManager.getCacheStats().currentSize());

        // Clear all
        preloadManager.clearCache();

        // Everything should be gone
        assertFalse(preloadManager.isRangeCached(SAMPLE_WAV, 1000, 2000));
        assertFalse(preloadManager.isRangeCached(SAMPLE_WAV, 2000, 3000));
        assertFalse(preloadManager.isRangeCached(SWEEP_WAV, 1000, 2000));
        assertEquals(0, preloadManager.getCacheStats().currentSize());
    }

    @Test
    void testCacheStats() throws Exception {
        FmodPreloadManager.CacheStats emptyStats = preloadManager.getCacheStats();
        assertEquals(0, emptyStats.currentSize());
        assertEquals(3, emptyStats.maxSize());
        assertEquals(0.0, emptyStats.getUtilization());
        assertFalse(emptyStats.isFull());

        // Add one entry
        preloadManager.preloadRange(SAMPLE_WAV, 1000, 2000).get(2, TimeUnit.SECONDS);
        FmodPreloadManager.CacheStats partialStats = preloadManager.getCacheStats();
        assertEquals(1, partialStats.currentSize());
        assertEquals(1.0 / 3.0, partialStats.getUtilization(), 0.01);
        assertFalse(partialStats.isFull());

        // Fill cache
        preloadManager.preloadRange(SAMPLE_WAV, 2000, 3000).get(2, TimeUnit.SECONDS);
        preloadManager.preloadRange(SAMPLE_WAV, 3000, 4000).get(2, TimeUnit.SECONDS);
        FmodPreloadManager.CacheStats fullStats = preloadManager.getCacheStats();
        assertEquals(3, fullStats.currentSize());
        assertEquals(1.0, fullStats.getUtilization());
        assertTrue(fullStats.isFull());
    }

    // ========== Thread Safety Tests ==========

    @Test
    @Timeout(5)
    void testConcurrentPreloadsSameRange() throws Exception {
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        Set<CompletableFuture<Void>> futures = ConcurrentHashMap.newKeySet();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(
                    () -> {
                        try {
                            startLatch.await();
                            // All threads try to preload same range
                            CompletableFuture<Void> future =
                                    preloadManager.preloadRange(SAMPLE_WAV, 1000, 2000);
                            futures.add(future);
                        } catch (Exception e) {
                            fail("Thread failed: " + e);
                        } finally {
                            doneLatch.countDown();
                        }
                    });
        }

        startLatch.countDown(); // Release all threads
        assertTrue(doneLatch.await(3, TimeUnit.SECONDS));

        // Wait for all futures
        for (CompletableFuture<Void> future : futures) {
            future.get(2, TimeUnit.SECONDS);
        }

        // Should only have one entry in cache
        assertEquals(1, preloadManager.getCacheStats().currentSize());
        assertTrue(preloadManager.isRangeCached(SAMPLE_WAV, 1000, 2000));

        executor.shutdown();
    }

    @Test
    @Timeout(5)
    void testConcurrentPreloadsDifferentRanges() throws Exception {
        int threadCount = 20;
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        AtomicReference<Exception> error = new AtomicReference<>();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(
                    () -> {
                        try {
                            barrier.await(); // Synchronize start

                            // Each thread preloads different range
                            CompletableFuture<Void> future =
                                    preloadManager.preloadRange(
                                            SAMPLE_WAV, index * 100, (index + 1) * 100);
                            future.get(2, TimeUnit.SECONDS);

                            successCount.incrementAndGet();
                        } catch (Exception e) {
                            error.set(e);
                        } finally {
                            doneLatch.countDown();
                        }
                    });
        }

        assertTrue(doneLatch.await(4, TimeUnit.SECONDS));
        assertNull(error.get(), "Thread threw exception");
        assertEquals(threadCount, successCount.get());

        // Only last 3 should be cached (LRU)
        FmodPreloadManager.CacheStats stats = preloadManager.getCacheStats();
        assertEquals(3, stats.currentSize());

        executor.shutdown();
    }

    @Test
    @Timeout(5)
    void testConcurrentPreloadAndGet() throws Exception {
        // Pre-cache one entry
        preloadManager.preloadRange(SAMPLE_WAV, 1000, 2000).get(2, TimeUnit.SECONDS);

        int iterations = 100;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        AtomicReference<Exception> error = new AtomicReference<>();

        // Thread 1: Continuously preload new ranges
        Thread preloader =
                new Thread(
                        () -> {
                            try {
                                startLatch.await();
                                for (int i = 0; i < iterations; i++) {
                                    preloadManager.preloadRange(SAMPLE_WAV, i * 100, (i + 1) * 100);
                                    Thread.sleep(1); // Small delay
                                }
                            } catch (Exception e) {
                                error.set(e);
                            } finally {
                                doneLatch.countDown();
                            }
                        });

        // Thread 2: Continuously get from cache
        Thread getter =
                new Thread(
                        () -> {
                            try {
                                startLatch.await();
                                for (int i = 0; i < iterations; i++) {
                                    preloadManager.getPreloadedSound(SAMPLE_WAV, 1000, 2000);
                                    // May or may not be present depending on eviction
                                    Thread.sleep(1);
                                }
                            } catch (Exception e) {
                                error.set(e);
                            } finally {
                                doneLatch.countDown();
                            }
                        });

        preloader.start();
        getter.start();
        startLatch.countDown();

        assertTrue(doneLatch.await(3, TimeUnit.SECONDS));
        assertNull(error.get(), "Thread threw exception");
    }

    // ========== Shutdown Behavior Tests ==========

    @Test
    void testShutdownCleansUp() throws Exception {
        // Load some sounds
        CompletableFuture<Void> f1 = preloadManager.preloadRange(SAMPLE_WAV, 1000, 2000);
        CompletableFuture<Void> f2 = preloadManager.preloadRange(SAMPLE_WAV, 2000, 3000);
        CompletableFuture.allOf(f1, f2).get(3, TimeUnit.SECONDS);

        assertTrue(preloadManager.getCacheStats().currentSize() > 0);
        assertTrue(preloadManager.getCacheStats().estimatedMemoryBytes() > 0);

        // Shutdown
        preloadManager.shutdown();

        // Everything should be cleared
        assertEquals(0, preloadManager.getCacheStats().currentSize());
        assertEquals(0, preloadManager.getCacheStats().estimatedMemoryBytes());
    }

    @Test
    void testOperationsAfterShutdown() throws Exception {
        preloadManager.shutdown();

        // All operations should handle shutdown gracefully

        // Preload returns completed future
        CompletableFuture<Void> future = preloadManager.preloadRange(SAMPLE_WAV, 1000, 2000);
        assertTrue(future.isDone());
        assertDoesNotThrow(() -> future.get(10, TimeUnit.MILLISECONDS));

        // Get returns empty
        Optional<Pointer> sound = preloadManager.getPreloadedSound(SAMPLE_WAV, 1000, 2000);
        assertFalse(sound.isPresent());

        // Is cached returns false
        assertFalse(preloadManager.isRangeCached(SAMPLE_WAV, 1000, 2000));

        // Clear operations don't throw
        assertDoesNotThrow(() -> preloadManager.clearFile(SAMPLE_WAV));
        assertDoesNotThrow(() -> preloadManager.clearCache());

        // Stats still work
        FmodPreloadManager.CacheStats stats = preloadManager.getCacheStats();
        assertEquals(0, stats.currentSize());
    }

    @Test
    void testDoubleShutdown() {
        // Double shutdown should be safe
        preloadManager.shutdown();
        assertDoesNotThrow(() -> preloadManager.shutdown());
    }

    // ========== PreloadKey Validation Tests ==========

    @Test
    void testPreloadKeyValidation() {
        // Negative start frame
        assertThrows(
                IllegalArgumentException.class,
                () -> new FmodPreloadManager.PreloadKey("file.wav", -1, 100));

        // End frame not greater than start
        assertThrows(
                IllegalArgumentException.class,
                () -> new FmodPreloadManager.PreloadKey("file.wav", 100, 100));

        assertThrows(
                IllegalArgumentException.class,
                () -> new FmodPreloadManager.PreloadKey("file.wav", 100, 50));

        // Valid key
        assertDoesNotThrow(() -> new FmodPreloadManager.PreloadKey("file.wav", 0, 100));
    }

    @Test
    void testPreloadKeyEquality() {
        FmodPreloadManager.PreloadKey key1 =
                new FmodPreloadManager.PreloadKey("file.wav", 100, 200);
        FmodPreloadManager.PreloadKey key2 =
                new FmodPreloadManager.PreloadKey("file.wav", 100, 200);
        FmodPreloadManager.PreloadKey key3 =
                new FmodPreloadManager.PreloadKey("other.wav", 100, 200);
        FmodPreloadManager.PreloadKey key4 =
                new FmodPreloadManager.PreloadKey("file.wav", 100, 300);

        // Same parameters should be equal
        assertEquals(key1, key2);
        assertEquals(key1.hashCode(), key2.hashCode());

        // Different parameters should not be equal
        assertNotEquals(key1, key3);
        assertNotEquals(key1, key4);
    }

    // ========== Direct Memory Waste Detection Tests ==========

    @Test
    void testMemoryWasteMultipleRangesFromSameFile() throws Exception {
        // This test should FAIL if different ranges from same file waste memory
        // by loading the entire file multiple times

        // Get baseline
        FmodPreloadManager.CacheStats baseline = preloadManager.getCacheStats();
        assertEquals(0, baseline.estimatedMemoryBytes());

        // Load a tiny 100-frame range (should be ~400 bytes at 16-bit stereo)
        preloadManager.preloadRange(SAMPLE_WAV, 0, 100).get(2, TimeUnit.SECONDS);

        long firstRangeMemory = preloadManager.getCacheStats().estimatedMemoryBytes();

        // CORRECT BEHAVIOR: Should only load the 100-frame range, not entire file
        // This assertion will FAIL because it actually loads the entire 3.9MB file
        assertTrue(
                firstRangeMemory < 10_000,
                "100-frame range should be <10KB, but was: "
                        + firstRangeMemory
                        + " bytes (entire file!)");

        // Load ANOTHER tiny 100-frame range from the SAME file
        preloadManager.preloadRange(SAMPLE_WAV, 5000, 5100).get(2, TimeUnit.SECONDS);

        long secondRangeMemory = preloadManager.getCacheStats().estimatedMemoryBytes();

        // CORRECT BEHAVIOR: Two 100-frame ranges should be ~800 bytes total
        // This will FAIL - it actually doubles to ~8MB
        assertTrue(
                secondRangeMemory < 20_000,
                "Two 100-frame ranges should be <20KB, but was: " + secondRangeMemory + " bytes");

        // Load a THIRD tiny range
        preloadManager.preloadRange(SAMPLE_WAV, 10000, 10100).get(2, TimeUnit.SECONDS);

        long thirdRangeMemory = preloadManager.getCacheStats().estimatedMemoryBytes();

        // CORRECT BEHAVIOR: Three 100-frame ranges should be ~1.2KB
        // This will FAIL - it actually triples to ~12MB
        assertTrue(
                thirdRangeMemory < 30_000,
                "Three 100-frame ranges should be <30KB, but was: " + thirdRangeMemory + " bytes");

        // Memory should be proportional to range sizes, not file count
        long expectedMemoryForRanges = 3 * 100 * 4; // 3 ranges × 100 frames × 4 bytes = 1200 bytes

        // Allow 10x overhead for metadata/structures, but not 10000x!
        assertTrue(
                thirdRangeMemory < expectedMemoryForRanges * 10,
                "Memory usage should be proportional to range size. Expected ~"
                        + expectedMemoryForRanges
                        + " bytes, got "
                        + thirdRangeMemory
                        + " bytes");
    }

    @Test
    void testCachedSoundContainsEntireFileNotJustRange() throws Exception {
        // This test should FAIL if a cached "range" contains the entire file
        // instead of just the requested range

        // Request a specific 1000-frame range
        preloadManager.preloadRange(SAMPLE_WAV, 1000, 2000).get(2, TimeUnit.SECONDS);

        // Get the supposedly "cached range"
        Optional<Pointer> cachedSound = preloadManager.getPreloadedSound(SAMPLE_WAV, 1000, 2000);
        assertTrue(cachedSound.isPresent());

        // Check the actual length of the cached sound
        com.sun.jna.ptr.IntByReference lengthRef = new com.sun.jna.ptr.IntByReference();
        int result =
                fmod.FMOD_Sound_GetLength(
                        cachedSound.get(), lengthRef, FmodConstants.FMOD_TIMEUNIT_PCM);
        assertEquals(FmodConstants.FMOD_OK, result);

        int actualFrames = lengthRef.getValue();

        // CORRECT BEHAVIOR: Cached sound should contain ONLY the requested 1000 frames
        // This will FAIL - it actually contains 80,000+ frames (entire file)
        assertEquals(
                1000,
                actualFrames,
                "Cached range should be exactly 1000 frames, but was "
                        + actualFrames
                        + " frames (entire file!)");
    }

    @Test
    void testSlightlyDifferentRangesDuplicateEntireFile() throws Exception {
        // This test should FAIL if slightly different ranges cause
        // unnecessary duplication of data

        // Load range 1000-2000
        preloadManager.preloadRange(SAMPLE_WAV, 1000, 2000).get(2, TimeUnit.SECONDS);
        long memoryAfterFirst = preloadManager.getCacheStats().estimatedMemoryBytes();

        // Load range 1000-2001 (just ONE frame different!)
        preloadManager.preloadRange(SAMPLE_WAV, 1000, 2001).get(2, TimeUnit.SECONDS);
        long memoryAfterSecond = preloadManager.getCacheStats().estimatedMemoryBytes();

        // CORRECT BEHAVIOR: One extra frame should add minimal memory (~4 bytes)
        // This will FAIL - it actually doubles the memory by loading entire file again
        long expectedIncrease = 4; // 1 frame × 4 bytes
        long actualIncrease = memoryAfterSecond - memoryAfterFirst;

        assertTrue(
                actualIncrease < 1000,
                "Adding one frame should increase memory by ~4 bytes, but increased by "
                        + actualIncrease
                        + " bytes (entire file duplicated!)");

        // These ranges overlap 99.9% - they should share data efficiently
        assertEquals(2, preloadManager.getCacheStats().currentSize());

        // Memory should not double for 99.9% overlap
        assertTrue(
                memoryAfterSecond < memoryAfterFirst * 1.1,
                "99.9% overlapping ranges should share memory efficiently. "
                        + "Memory went from "
                        + memoryAfterFirst
                        + " to "
                        + memoryAfterSecond);
    }
}
