package waveform;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.concurrent.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class WaveformSegmentCacheTest {

    private WaveformSegmentCache cache;
    private ViewportContext viewport;
    private Image mockImage;

    @BeforeEach
    void setUp() {
        viewport =
                new ViewportContext(
                        0.0, 10.0, 1000, 200, 100, ViewportContext.ScrollDirection.FORWARD);
        cache = new WaveformSegmentCache(viewport);
        mockImage = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
    }

    @Test
    void testBasicPutAndGet() {
        var key = new WaveformSegmentCache.SegmentKey(0.0, 100, 200);
        var future = CompletableFuture.completedFuture(mockImage);

        cache.put(key, future);
        assertEquals(future, cache.get(key));
    }

    @Test
    void testGetNonExistentKey() {
        var key = new WaveformSegmentCache.SegmentKey(5.0, 100, 200);
        assertNull(cache.get(key));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testCircularOverwrite() {
        // Fill cache beyond capacity to test circular overwrite
        // Cache size = 5 visible + 4 prefetch = 9 slots
        var futures = new CompletableFuture[15];
        for (int i = 0; i < 15; i++) {
            var key = new WaveformSegmentCache.SegmentKey(i * 2.0, 100, 200);
            futures[i] = CompletableFuture.completedFuture(mockImage);
            cache.put(key, futures[i]);
        }

        // Early entries should be overwritten
        assertNull(cache.get(new WaveformSegmentCache.SegmentKey(0.0, 100, 200)));
        assertNull(cache.get(new WaveformSegmentCache.SegmentKey(2.0, 100, 200)));

        // Recent entries should still exist
        assertNotNull(cache.get(new WaveformSegmentCache.SegmentKey(28.0, 100, 200)));
    }

    @Test
    void testDuplicateKeyUpdate() {
        var key = new WaveformSegmentCache.SegmentKey(0.0, 100, 200);
        var future1 = CompletableFuture.completedFuture(mockImage);
        var future2 = CompletableFuture.completedFuture(mockImage);

        cache.put(key, future1);
        cache.put(key, future2);

        // Should update existing entry, not create duplicate
        assertEquals(future2, cache.get(key));

        // Verify no duplicates by filling cache
        for (int i = 1; i < 20; i++) {
            cache.put(
                    new WaveformSegmentCache.SegmentKey(i * 2.0, 100, 200),
                    CompletableFuture.completedFuture(mockImage));
        }

        // Original key should still return future2 if within cache size
        var result = cache.get(key);
        assertTrue(result == null || result == future2);
    }

    @Test
    void testClear() {
        // Add multiple entries
        for (int i = 0; i < 5; i++) {
            var key = new WaveformSegmentCache.SegmentKey(i * 2.0, 100, 200);
            cache.put(key, CompletableFuture.completedFuture(mockImage));
        }

        cache.clear();

        // All entries should be gone
        for (int i = 0; i < 5; i++) {
            var key = new WaveformSegmentCache.SegmentKey(i * 2.0, 100, 200);
            assertNull(cache.get(key));
        }
    }

    @Test
    void testViewportPixelsPerSecondChangeClearsCache() {
        var key = new WaveformSegmentCache.SegmentKey(0.0, 100, 200);
        cache.put(key, CompletableFuture.completedFuture(mockImage));

        // Change pixels per second
        var newViewport =
                new ViewportContext(
                        0.0, 10.0, 1000, 200, 200, ViewportContext.ScrollDirection.FORWARD);
        cache.updateViewport(newViewport);

        // Cache should be cleared
        assertNull(cache.get(key));
    }

    @Test
    void testViewportHeightChangeClearsCache() {
        var key = new WaveformSegmentCache.SegmentKey(0.0, 100, 200);
        cache.put(key, CompletableFuture.completedFuture(mockImage));

        // Change height
        var newViewport =
                new ViewportContext(
                        0.0, 10.0, 1000, 400, 100, ViewportContext.ScrollDirection.FORWARD);
        cache.updateViewport(newViewport);

        // Cache should be cleared
        assertNull(cache.get(key));
    }

    @Test
    void testViewportWidthChangePreservesEntries() {
        var key1 = new WaveformSegmentCache.SegmentKey(0.0, 100, 200);
        var key2 = new WaveformSegmentCache.SegmentKey(2.0, 100, 200);
        var future1 = CompletableFuture.completedFuture(mockImage);
        var future2 = CompletableFuture.completedFuture(mockImage);

        cache.put(key1, future1);
        cache.put(key2, future2);

        // Change width only
        var newViewport =
                new ViewportContext(
                        0.0, 10.0, 1600, 200, 100, ViewportContext.ScrollDirection.FORWARD);
        cache.updateViewport(newViewport);

        // Entries should be preserved
        assertEquals(future1, cache.get(key1));
        assertEquals(future2, cache.get(key2));
    }

    @Test
    void testViewportPositionChangeNoEffect() {
        var key = new WaveformSegmentCache.SegmentKey(0.0, 100, 200);
        var future = CompletableFuture.completedFuture(mockImage);
        cache.put(key, future);

        // Change only position
        var newViewport =
                new ViewportContext(
                        5.0, 15.0, 1000, 200, 100, ViewportContext.ScrollDirection.FORWARD);
        cache.updateViewport(newViewport);

        // Entry should still exist
        assertEquals(future, cache.get(key));
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 400, 800, 1600, 3200})
    void testVariousViewportWidths(int width) {
        viewport =
                new ViewportContext(
                        0.0, 10.0, width, 200, 100, ViewportContext.ScrollDirection.FORWARD);
        cache = new WaveformSegmentCache(viewport);

        // Expected cache size = ceil(width/200) + 4
        int expectedVisible = (int) Math.ceil(width / 200.0);
        int expectedSize = expectedVisible + 4;

        // Fill cache to capacity
        for (int i = 0; i < expectedSize; i++) {
            var key = new WaveformSegmentCache.SegmentKey(i * 2.0, 100, 200);
            cache.put(key, CompletableFuture.completedFuture(mockImage));
        }

        // All should fit
        for (int i = 0; i < expectedSize; i++) {
            var key = new WaveformSegmentCache.SegmentKey(i * 2.0, 100, 200);
            assertNotNull(cache.get(key), "Entry " + i + " should exist");
        }

        // Add one more - should overwrite oldest
        var overflowKey = new WaveformSegmentCache.SegmentKey(expectedSize * 2.0, 100, 200);
        cache.put(overflowKey, CompletableFuture.completedFuture(mockImage));
        assertNotNull(cache.get(overflowKey));
    }

    @Test
    void testConcurrentReadsAndWrites() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(100);

        // 50 writer tasks
        for (int i = 0; i < 50; i++) {
            final int index = i;
            executor.submit(
                    () -> {
                        try {
                            var key = new WaveformSegmentCache.SegmentKey(index * 0.1, 100, 200);
                            cache.put(key, CompletableFuture.completedFuture(mockImage));
                        } finally {
                            latch.countDown();
                        }
                    });
        }

        // 50 reader tasks
        for (int i = 0; i < 50; i++) {
            final int index = i;
            executor.submit(
                    () -> {
                        try {
                            var key = new WaveformSegmentCache.SegmentKey(index * 0.1, 100, 200);
                            cache.get(key); // May or may not find it
                        } finally {
                            latch.countDown();
                        }
                    });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Operations should complete quickly");
        executor.shutdown();
    }

    @Test
    void testConcurrentUpdateViewport() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch latch = new CountDownLatch(30);

        // Continuous reads
        for (int i = 0; i < 10; i++) {
            executor.submit(
                    () -> {
                        try {
                            for (int j = 0; j < 10; j++) {
                                var key = new WaveformSegmentCache.SegmentKey(j * 0.5, 100, 200);
                                cache.get(key);
                                Thread.sleep(1);
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            latch.countDown();
                        }
                    });
        }

        // Continuous writes
        for (int i = 0; i < 10; i++) {
            final int index = i;
            executor.submit(
                    () -> {
                        try {
                            var key = new WaveformSegmentCache.SegmentKey(index * 0.5, 100, 200);
                            cache.put(key, CompletableFuture.completedFuture(mockImage));
                            Thread.sleep(5);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            latch.countDown();
                        }
                    });
        }

        // Viewport updates
        for (int i = 0; i < 10; i++) {
            final int width = 1000 + i * 100;
            executor.submit(
                    () -> {
                        try {
                            var newViewport =
                                    new ViewportContext(
                                            0.0,
                                            10.0,
                                            width,
                                            200,
                                            100,
                                            ViewportContext.ScrollDirection.FORWARD);
                            cache.updateViewport(newViewport);
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            latch.countDown();
                        }
                    });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS), "No deadlock should occur");
        executor.shutdown();
    }

    @Test
    void testPendingFutures() {
        var key = new WaveformSegmentCache.SegmentKey(0.0, 100, 200);
        var future = new CompletableFuture<Image>();

        cache.put(key, future);
        var retrieved = cache.get(key);

        assertSame(future, retrieved);
        assertFalse(future.isDone());

        // Complete the future
        future.complete(mockImage);
        assertTrue(retrieved.isDone());
        assertEquals(mockImage, retrieved.getNow(null));
    }

    @Test
    void testSegmentKeyEquality() {
        var key1 = new WaveformSegmentCache.SegmentKey(0.0, 100, 200);
        var key2 = new WaveformSegmentCache.SegmentKey(0.0, 100, 200);
        var key3 = new WaveformSegmentCache.SegmentKey(0.0, 100, 300);

        assertEquals(key1, key2);
        assertNotEquals(key1, key3);
    }

    @Test
    void testSegmentKeyDuration() {
        var key = new WaveformSegmentCache.SegmentKey(0.0, 100, 200);
        assertEquals(2.0, key.duration(), 0.001);
        assertEquals(2.0, key.endTime(), 0.001);

        var key2 = new WaveformSegmentCache.SegmentKey(5.0, 200, 200);
        assertEquals(1.0, key2.duration(), 0.001);
        assertEquals(6.0, key2.endTime(), 0.001);
    }

    @Test
    void testHeadPointerWraparound() {
        // Test the subtle head = (head + 1 >= size) ? 0 : head + 1 logic
        var smallViewport =
                new ViewportContext(
                        0.0, 10.0, 200, 200, 100, ViewportContext.ScrollDirection.FORWARD);
        var smallCache = new WaveformSegmentCache(smallViewport); // size = 1 + 4 = 5

        // Fill exactly to size
        for (int i = 0; i < 5; i++) {
            smallCache.put(
                    new WaveformSegmentCache.SegmentKey(i, 100, 200),
                    CompletableFuture.completedFuture(mockImage));
        }

        // Verify next put wraps to index 0
        var key = new WaveformSegmentCache.SegmentKey(99, 100, 200);
        smallCache.put(key, CompletableFuture.completedFuture(mockImage));

        // First entry should be overwritten
        assertNull(smallCache.get(new WaveformSegmentCache.SegmentKey(0, 100, 200)));
        assertNotNull(smallCache.get(key));
    }

    @Test
    void testResizeWhileCacheFull() {
        // Fill cache completely (size = 5 + 4 = 9)
        for (int i = 0; i < 9; i++) {
            cache.put(
                    new WaveformSegmentCache.SegmentKey(i, 100, 200),
                    CompletableFuture.completedFuture(mockImage));
        }

        // Shrink viewport - resize() must handle head > newSize
        var smaller =
                new ViewportContext(
                        0.0, 10.0, 400, 200, 100, ViewportContext.ScrollDirection.FORWARD);
        cache.updateViewport(smaller); // size 9 -> 6

        // Verify cache still functional after resize
        var testKey = new WaveformSegmentCache.SegmentKey(100, 100, 200);
        cache.put(testKey, CompletableFuture.completedFuture(mockImage));
        assertNotNull(cache.get(testKey));
    }

    @Test
    void testUpdateViewportBeforeAnyPuts() {
        // Immediate viewport change - currentViewport is set, but entries array empty
        var newViewport =
                new ViewportContext(
                        0.0, 10.0, 2000, 200, 100, ViewportContext.ScrollDirection.FORWARD);

        // Should not NPE even though no entries exist yet
        assertDoesNotThrow(() -> cache.updateViewport(newViewport));

        // Cache should work normally after
        var key = new WaveformSegmentCache.SegmentKey(0, 100, 200);
        cache.put(key, CompletableFuture.completedFuture(mockImage));
        assertNotNull(cache.get(key));
    }

    @Test
    void testClearCancelsPendingFutures() {
        // Create futures that will track if they were cancelled
        var future1 = new CompletableFuture<Image>();
        var future2 = new CompletableFuture<Image>();
        var future3 = new CompletableFuture<Image>();

        // Add them to cache
        cache.put(new WaveformSegmentCache.SegmentKey(0.0, 100, 200), future1);
        cache.put(new WaveformSegmentCache.SegmentKey(2.0, 100, 200), future2);
        cache.put(new WaveformSegmentCache.SegmentKey(4.0, 100, 200), future3);

        // Clear should cancel all pending futures
        cache.clear();

        // Verify all futures were cancelled
        assertTrue(future1.isCancelled(), "Future 1 should be cancelled after clear()");
        assertTrue(future2.isCancelled(), "Future 2 should be cancelled after clear()");
        assertTrue(future3.isCancelled(), "Future 3 should be cancelled after clear()");
    }

    @Test
    void testClearCancelsRunningComputations() throws Exception {
        var startedLatch = new CountDownLatch(1);

        // Create a future that simulates long-running computation
        var future =
                CompletableFuture.supplyAsync(
                        () -> {
                            startedLatch.countDown();
                            // Simulate expensive waveform rendering
                            for (int i = 0; i < 100; i++) {
                                if (Thread.currentThread().isInterrupted()) {
                                    throw new CancellationException("Rendering cancelled");
                                }
                                try {
                                    Thread.sleep(10); // Simulate work
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    throw new CancellationException("Rendering interrupted");
                                }
                            }
                            return mockImage;
                        });

        cache.put(new WaveformSegmentCache.SegmentKey(0.0, 100, 200), future);

        // Wait for computation to start
        assertTrue(startedLatch.await(1, TimeUnit.SECONDS));

        // Clear should cancel the running computation
        cache.clear();

        // Verify future was cancelled
        assertTrue(future.isCancelled(), "Running computation should be cancelled");

        // Verify the computation actually stopped (should throw CancellationException)
        assertThrows(
                CancellationException.class,
                () -> future.get(100, TimeUnit.MILLISECONDS),
                "Cancelled future should throw CancellationException");
    }

    @Test
    void testConcurrentDuplicateKeyPuts() throws Exception {
        // Race condition: two threads find key doesn't exist, both try to add
        var key = new WaveformSegmentCache.SegmentKey(0, 100, 200);
        var barrier = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);

        var image1 = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        var image2 = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);

        try {
            var future1 =
                    executor.submit(
                            () -> {
                                try {
                                    barrier.await();
                                    cache.put(key, CompletableFuture.completedFuture(image1));
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            });

            var future2 =
                    executor.submit(
                            () -> {
                                try {
                                    barrier.await();
                                    cache.put(key, CompletableFuture.completedFuture(image2));
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            });

            future1.get(5, TimeUnit.SECONDS);
            future2.get(5, TimeUnit.SECONDS);

            // Should have exactly one entry, not corrupted state
            var result = cache.get(key);
            assertNotNull(result);
            var resultImage = result.getNow(null);
            assertTrue(
                    resultImage == image1 || resultImage == image2,
                    "Should be one of the two images");
        } finally {
            executor.shutdown();
        }
    }

    @SuppressWarnings("unchecked")
    @ParameterizedTest
    @MethodSource("resizeTestCases")
    void testResizeScenarios(int initialWidth, int newWidth, int entriesToAdd) {
        // Create initial cache
        viewport =
                new ViewportContext(
                        0.0, 10.0, initialWidth, 200, 100, ViewportContext.ScrollDirection.FORWARD);
        cache = new WaveformSegmentCache(viewport);

        // Add entries
        var futures = new CompletableFuture[entriesToAdd];
        for (int i = 0; i < entriesToAdd; i++) {
            var key = new WaveformSegmentCache.SegmentKey(i * 1.0, 100, 200);
            futures[i] = CompletableFuture.completedFuture(mockImage);
            cache.put(key, futures[i]);
        }

        // Resize
        var newViewport =
                new ViewportContext(
                        0.0, 10.0, newWidth, 200, 100, ViewportContext.ScrollDirection.FORWARD);
        cache.updateViewport(newViewport);

        // Verify entries are preserved (up to new cache size)
        int newCacheSize = (int) Math.ceil(newWidth / 200.0) + 4;

        int found = 0;
        for (int i = 0; i < entriesToAdd; i++) {
            var key = new WaveformSegmentCache.SegmentKey(i * 1.0, 100, 200);
            if (cache.get(key) != null) {
                found++;
            }
        }

        assertTrue(found <= newCacheSize, "Should not exceed new cache size");
        if (entriesToAdd <= newCacheSize) {
            assertEquals(entriesToAdd, found, "All entries should be preserved when growing");
        }
    }

    static Stream<Arguments> resizeTestCases() {
        return Stream.of(
                Arguments.of(400, 800, 3), // Grow with few entries
                Arguments.of(400, 800, 10), // Grow with many entries
                Arguments.of(800, 400, 5), // Shrink with moderate entries
                Arguments.of(800, 400, 15), // Shrink with many entries
                Arguments.of(600, 600, 5) // Same size
                );
    }
}
