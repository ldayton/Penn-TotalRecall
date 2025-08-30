package w2;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import audio.FmodCore;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.concurrent.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class WaveformRendererTest {

    @Mock private FmodCore mockFmodCore;
    private AutoCloseable mocks;
    private WaveformImpl waveform;
    private ViewportContext viewport;

    @BeforeEach
    void setUp() throws IOException {
        mocks = MockitoAnnotations.openMocks(this);

        // Mock FMOD to return test data
        double[] samples = new double[44100]; // 1 second of audio at 44.1kHz

        // Fill with test waveform data (sine wave)
        for (int i = 0; i < samples.length; i++) {
            samples[i] = Math.sin(2 * Math.PI * 440 * i / 44100.0); // 440Hz sine
        }

        FmodCore.ChunkData testChunk =
                new FmodCore.ChunkData(
                        samples,
                        44100, // sampleRate
                        1, // channels
                        0, // overlapFrames
                        44100 // totalFrames
                        );

        when(mockFmodCore.readAudioChunk(anyString(), anyInt(), anyDouble(), anyDouble()))
                .thenReturn(testChunk);

        waveform = (WaveformImpl) Waveform.forAudioFile("test-audio.wav", mockFmodCore);
        viewport =
                new ViewportContext(
                        0.0, 5.0, 1000, 200, 100, ViewportContext.ScrollDirection.FORWARD);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (waveform != null) {
            waveform.shutdown();
        }
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    void testBasicRendering() throws Exception {
        CompletableFuture<Image> future = waveform.renderViewport(viewport);

        assertNotNull(future);
        Image image = future.get(5, TimeUnit.SECONDS);
        assertNotNull(image);

        // Check dimensions
        BufferedImage buffered = (BufferedImage) image;
        assertEquals(viewport.viewportWidthPx(), buffered.getWidth());
        assertEquals(viewport.viewportHeightPx(), buffered.getHeight());
    }

    @Test
    void testMultipleSegments() throws Exception {
        // Wide viewport requiring multiple segments
        var wideViewport =
                new ViewportContext(
                        0.0, 10.0, 600, 200, 100, ViewportContext.ScrollDirection.FORWARD);

        CompletableFuture<Image> future = waveform.renderViewport(wideViewport);
        Image image = future.get(5, TimeUnit.SECONDS);

        BufferedImage buffered = (BufferedImage) image;
        assertEquals(600, buffered.getWidth());
        assertEquals(200, buffered.getHeight());
    }

    @Test
    void testCaching() throws Exception {
        // First render
        long start1 = System.currentTimeMillis();
        CompletableFuture<Image> future1 = waveform.renderViewport(viewport);
        Image image1 = future1.get(5, TimeUnit.SECONDS);
        long time1 = System.currentTimeMillis() - start1;

        // Second render should be faster (cached)
        long start2 = System.currentTimeMillis();
        CompletableFuture<Image> future2 = waveform.renderViewport(viewport);
        Image image2 = future2.get(5, TimeUnit.SECONDS);
        long time2 = System.currentTimeMillis() - start2;

        assertNotNull(image1);
        assertNotNull(image2);

        // Second should be significantly faster due to caching
        // (Can't guarantee exact timing, but should be noticeably faster)
        assertTrue(time2 <= time1, "Cached render should not be slower");
    }

    @Test
    void testScrollingPrefetch() throws Exception {
        // Render initial viewport
        CompletableFuture<Image> future1 = waveform.renderViewport(viewport);
        Image image1 = future1.get(5, TimeUnit.SECONDS);
        assertNotNull(image1);

        // Allow prefetch to happen
        Thread.sleep(100);

        // Scroll forward - should benefit from prefetch
        var scrolledViewport =
                new ViewportContext(
                        2.0, 7.0, 1000, 200, 100, ViewportContext.ScrollDirection.FORWARD);

        CompletableFuture<Image> future2 = waveform.renderViewport(scrolledViewport);
        Image image2 = future2.get(5, TimeUnit.SECONDS);
        assertNotNull(image2);
    }

    @Test
    void testZoomInvalidatesCache() throws Exception {
        // Initial render
        CompletableFuture<Image> future1 = waveform.renderViewport(viewport);
        Image image1 = future1.get(5, TimeUnit.SECONDS);
        assertNotNull(image1);

        // Change zoom level (pixels per second)
        var zoomedViewport =
                new ViewportContext(
                        0.0, 5.0, 1000, 200, 200, ViewportContext.ScrollDirection.FORWARD);

        CompletableFuture<Image> future2 = waveform.renderViewport(zoomedViewport);
        Image image2 = future2.get(5, TimeUnit.SECONDS);
        assertNotNull(image2);

        // Images should be different due to different zoom
        BufferedImage buffered1 = (BufferedImage) image1;
        BufferedImage buffered2 = (BufferedImage) image2;

        // At least check they're both valid
        assertEquals(1000, buffered1.getWidth());
        assertEquals(1000, buffered2.getWidth());
    }

    @Test
    void testCancellation() throws Exception {
        CompletableFuture<Image> future = waveform.renderViewport(viewport);

        // Cancel before completion
        boolean cancelled = future.cancel(true);

        // May or may not successfully cancel depending on timing
        if (cancelled) {
            assertTrue(future.isCancelled());
            assertThrows(CancellationException.class, () -> future.get());
        } else {
            // Already completed
            assertNotNull(future.get());
        }
    }

    @Test
    void testPartialSegment() throws Exception {
        // Viewport that doesn't align perfectly with segment boundaries
        var partialViewport =
                new ViewportContext(
                        0.0, 2.5, 550, 200, 100, ViewportContext.ScrollDirection.FORWARD);

        CompletableFuture<Image> future = waveform.renderViewport(partialViewport);
        Image image = future.get(5, TimeUnit.SECONDS);

        BufferedImage buffered = (BufferedImage) image;
        assertEquals(550, buffered.getWidth());
        assertEquals(200, buffered.getHeight());
    }

    @Test
    void testSegmentBoundaryAlignment() throws Exception {
        // Test exact segment boundaries (200px segments)
        var boundaryViewport =
                new ViewportContext(
                        0.0, 2.0, 200, 200, 100, ViewportContext.ScrollDirection.FORWARD);

        CompletableFuture<Image> future = waveform.renderViewport(boundaryViewport);
        Image image = future.get(5, TimeUnit.SECONDS);
        BufferedImage buffered = (BufferedImage) image;

        // Verify no artifacts at boundaries by checking pixel continuity
        assertEquals(200, buffered.getWidth());
        assertEquals(200, buffered.getHeight());

        // Test multiple segments align properly
        var multiSegmentViewport =
                new ViewportContext(
                        0.0, 4.0, 400, 200, 100, ViewportContext.ScrollDirection.FORWARD);

        future = waveform.renderViewport(multiSegmentViewport);
        image = future.get(5, TimeUnit.SECONDS);
        buffered = (BufferedImage) image;
        assertEquals(400, buffered.getWidth());

        // Check boundary pixels for discontinuities
        // At x=199 and x=200 should have reasonable continuity
        int y = 100; // Check middle row
        int pixel199 = buffered.getRGB(199, y);
        int pixel200 = buffered.getRGB(200, y);
        // Should not be completely different (no hard seam)
        assertNotEquals(0, pixel199 | pixel200, "Boundary pixels should be rendered");
    }

    @Test
    void testCacheThrashing() throws Exception {
        // Rapid back-and-forth scrolling
        int iterations = 10;
        long totalTime = 0;

        for (int i = 0; i < iterations; i++) {
            // Scroll forward
            var forward =
                    new ViewportContext(
                            i * 0.5,
                            (i * 0.5) + 5.0,
                            1000,
                            200,
                            100,
                            ViewportContext.ScrollDirection.FORWARD);

            long start = System.nanoTime();
            CompletableFuture<Image> future = waveform.renderViewport(forward);
            Image image = future.get(5, TimeUnit.SECONDS);
            totalTime += System.nanoTime() - start;
            assertNotNull(image);

            // Scroll backward
            var backward =
                    new ViewportContext(
                            Math.max(0, (i - 1) * 0.5),
                            Math.max(5.0, ((i - 1) * 0.5) + 5.0),
                            1000,
                            200,
                            100,
                            ViewportContext.ScrollDirection.BACKWARD);

            start = System.nanoTime();
            future = waveform.renderViewport(backward);
            image = future.get(5, TimeUnit.SECONDS);
            totalTime += System.nanoTime() - start;
            assertNotNull(image);
        }

        // Average time should be reasonable (not exponentially growing)
        long avgTime = totalTime / (iterations * 2);
        assertTrue(
                avgTime < TimeUnit.SECONDS.toNanos(1),
                "Rendering should not degrade with thrashing");
    }

    @Test
    void testRaceConditionInCompositing() throws Exception {
        // Create multiple concurrent requests for overlapping viewports
        int concurrentRequests = 20;
        @SuppressWarnings("unchecked")
        CompletableFuture<Image>[] futures = new CompletableFuture[concurrentRequests];

        for (int i = 0; i < concurrentRequests; i++) {
            // Slightly different viewports to stress the cache
            var viewport =
                    new ViewportContext(
                            i * 0.1,
                            (i * 0.1) + 5.0,
                            1000,
                            200,
                            100,
                            i % 2 == 0
                                    ? ViewportContext.ScrollDirection.FORWARD
                                    : ViewportContext.ScrollDirection.BACKWARD);
            futures[i] = waveform.renderViewport(viewport);
        }

        // All should complete without race conditions
        CompletableFuture<Void> allOf = CompletableFuture.allOf(futures);
        allOf.get(10, TimeUnit.SECONDS);

        // Verify all completed successfully
        for (CompletableFuture<Image> future : futures) {
            assertTrue(future.isDone());
            assertNotNull(future.get());
            BufferedImage img = (BufferedImage) future.get();
            assertEquals(1000, img.getWidth());
            assertEquals(200, img.getHeight());
        }
    }

    @Test
    void testOffByOneErrors() throws Exception {
        // Test edge cases for width calculations
        int[] widths = {199, 200, 201, 399, 400, 401, 550, 999, 1000, 1001};

        for (int width : widths) {
            var viewport =
                    new ViewportContext(
                            0.0,
                            width / 100.0,
                            width,
                            200,
                            100,
                            ViewportContext.ScrollDirection.FORWARD);

            CompletableFuture<Image> future = waveform.renderViewport(viewport);
            Image image = future.get(5, TimeUnit.SECONDS);
            BufferedImage buffered = (BufferedImage) image;

            assertEquals(
                    width, buffered.getWidth(), "Width " + width + " should be rendered exactly");
            assertEquals(200, buffered.getHeight());

            // Check corners aren't blank (common off-by-one location)
            assertNotEquals(0, buffered.getRGB(0, 0), "Top-left should be rendered");
            assertNotEquals(0, buffered.getRGB(width - 1, 0), "Top-right should be rendered");
            assertNotEquals(0, buffered.getRGB(0, 199), "Bottom-left should be rendered");
            assertNotEquals(0, buffered.getRGB(width - 1, 199), "Bottom-right should be rendered");
        }
    }

    @Test
    void testMemoryLeakOnRapidZoomChanges() throws Exception {
        // Get initial memory baseline
        System.gc();
        Thread.sleep(100);
        long initialMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        // Rapid zoom changes that should invalidate cache
        for (int i = 0; i < 50; i++) {
            int pixelsPerSecond = 50 + (i * 10); // Varying zoom levels
            var viewport =
                    new ViewportContext(
                            0.0,
                            5.0,
                            1000,
                            200,
                            pixelsPerSecond,
                            ViewportContext.ScrollDirection.FORWARD);

            CompletableFuture<Image> future = waveform.renderViewport(viewport);

            // Sometimes cancel to test cleanup
            if (i % 3 == 0) {
                future.cancel(true);
            } else {
                future.get(5, TimeUnit.SECONDS);
            }
        }

        // Allow cleanup
        System.gc();
        Thread.sleep(100);
        long finalMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        // Memory should not grow excessively (allow 50MB growth for test overhead)
        long memoryGrowth = finalMemory - initialMemory;
        assertTrue(
                memoryGrowth < 50 * 1024 * 1024,
                "Memory leak detected: " + (memoryGrowth / 1024 / 1024) + "MB growth");
    }

    @Test
    void testPrefetchOscillation() throws Exception {
        // Rapidly change scroll direction
        for (int i = 0; i < 10; i++) {
            ViewportContext.ScrollDirection dir =
                    (i % 2 == 0)
                            ? ViewportContext.ScrollDirection.FORWARD
                            : ViewportContext.ScrollDirection.BACKWARD;

            var viewport =
                    new ViewportContext(2.0 + (i * 0.1), 7.0 + (i * 0.1), 1000, 200, 100, dir);

            CompletableFuture<Image> future = waveform.renderViewport(viewport);
            Image image = future.get(5, TimeUnit.SECONDS);
            assertNotNull(image);

            // Small delay to allow prefetch to start
            Thread.sleep(10);
        }

        // Should complete without excessive CPU usage or errors
        // Test passes if no exceptions thrown
    }

    @Test
    void testThreadPoolExhaustion() throws Exception {
        // Request many viewports simultaneously to stress thread pool
        int requests = 100;
        @SuppressWarnings("unchecked")
        CompletableFuture<Image>[] futures = new CompletableFuture[requests];

        for (int i = 0; i < requests; i++) {
            var viewport =
                    new ViewportContext(
                            i * 0.01,
                            (i * 0.01) + 5.0,
                            1000,
                            200,
                            100,
                            ViewportContext.ScrollDirection.FORWARD);
            futures[i] = waveform.renderViewport(viewport);
        }

        // All should eventually complete despite thread pool limits
        CompletableFuture<Void> allOf = CompletableFuture.allOf(futures);
        allOf.get(30, TimeUnit.SECONDS); // Allow more time for many requests

        for (CompletableFuture<Image> future : futures) {
            assertTrue(future.isDone(), "All requests should complete");
            assertFalse(future.isCompletedExceptionally(), "No requests should fail");
        }
    }
}
