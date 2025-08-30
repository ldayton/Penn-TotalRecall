package w2;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Priority-based waveform renderer with prefetch strategy. Fills segment cache efficiently using
 * industry-standard approaches.
 */
class WaveformRenderer {

    private static final int SEGMENT_WIDTH_PX = 200;
    private static final int PREFETCH_COUNT = 2; // Segments to prefetch in each direction

    private final WaveformSegmentCache cache;
    private final ExecutorService renderPool;
    private final String audioFilePath;

    enum Priority {
        VISIBLE(1),
        PREFETCH_SCROLL_DIRECTION(2),
        PREFETCH_OPPOSITE_SCROLL_DIRECTION(3);

        private final int value;

        Priority(int value) {
            this.value = value;
        }

        int getValue() {
            return value;
        }
    }

    record RenderTask(WaveformSegmentCache.SegmentKey key, Priority priority)
            implements Comparable<RenderTask> {

        @Override
        public int compareTo(RenderTask other) {
            return Integer.compare(this.priority.getValue(), other.priority.getValue());
        }
    }

    WaveformRenderer(String audioFilePath, WaveformSegmentCache cache, ExecutorService renderPool) {
        this.audioFilePath = audioFilePath;
        this.cache = cache;
        this.renderPool = renderPool;
    }

    /** Fill cache for viewport with priority-based rendering. */
    CompletableFuture<Image> renderViewport(ViewportContext viewport) {
        // Update cache for new viewport
        cache.updateViewport(viewport);

        // Calculate visible segments
        List<WaveformSegmentCache.SegmentKey> visibleSegments = calculateVisibleSegments(viewport);

        // Get or render each segment
        List<CompletableFuture<Image>> segmentFutures = new ArrayList<>();
        for (var key : visibleSegments) {
            CompletableFuture<Image> future = cache.get(key);
            if (future == null) {
                future = renderSegment(key);
                cache.put(key, future);
            }
            segmentFutures.add(future);
        }

        // Start prefetch tasks asynchronously
        CompletableFuture.runAsync(
                () -> {
                    addPrefetchTasks(viewport);
                },
                renderPool);

        // Composite segments when all ready
        return CompletableFuture.allOf(segmentFutures.toArray(CompletableFuture[]::new))
                .thenApply(
                        v -> {
                            List<Image> segments =
                                    segmentFutures.stream().map(f -> f.getNow(null)).toList();
                            return compositeSegments(segments, viewport);
                        });
    }

    /** Calculate which segments are needed for the viewport. */
    private List<WaveformSegmentCache.SegmentKey> calculateVisibleSegments(
            ViewportContext viewport) {
        List<WaveformSegmentCache.SegmentKey> segments = new ArrayList<>();

        double segmentDuration = (double) SEGMENT_WIDTH_PX / viewport.pixelsPerSecond();
        double currentTime = viewport.startTimeSeconds();

        while (currentTime < viewport.endTimeSeconds()) {
            segments.add(
                    new WaveformSegmentCache.SegmentKey(
                            currentTime, viewport.pixelsPerSecond(), viewport.viewportHeightPx()));
            currentTime += segmentDuration;
        }

        return segments;
    }

    /** Add prefetch tasks based on scroll direction. */
    private void addPrefetchTasks(ViewportContext viewport) {
        double segmentDuration = (double) SEGMENT_WIDTH_PX / viewport.pixelsPerSecond();

        if (viewport.scrollDirection() == ViewportContext.ScrollDirection.FORWARD) {
            // Prefetch forward (higher priority)
            double prefetchStart = viewport.endTimeSeconds();
            for (int i = 0; i < PREFETCH_COUNT; i++) {
                var key =
                        new WaveformSegmentCache.SegmentKey(
                                prefetchStart + i * segmentDuration,
                                viewport.pixelsPerSecond(),
                                viewport.viewportHeightPx());
                if (cache.get(key) == null) {
                    CompletableFuture<Image> future = renderSegment(key);
                    cache.put(key, future);
                }
            }

            // Prefetch backward (lower priority)
            double backStart = viewport.startTimeSeconds() - segmentDuration;
            for (int i = 0; i < PREFETCH_COUNT / 2; i++) {
                var key =
                        new WaveformSegmentCache.SegmentKey(
                                backStart - i * segmentDuration,
                                viewport.pixelsPerSecond(),
                                viewport.viewportHeightPx());
                if (cache.get(key) == null) {
                    CompletableFuture<Image> future = renderSegment(key);
                    cache.put(key, future);
                }
            }
        } else {
            // Scrolling backward - reverse priorities
            double backStart = viewport.startTimeSeconds() - segmentDuration;
            for (int i = 0; i < PREFETCH_COUNT; i++) {
                var key =
                        new WaveformSegmentCache.SegmentKey(
                                backStart - i * segmentDuration,
                                viewport.pixelsPerSecond(),
                                viewport.viewportHeightPx());
                if (cache.get(key) == null) {
                    CompletableFuture<Image> future = renderSegment(key);
                    cache.put(key, future);
                }
            }

            double forwardStart = viewport.endTimeSeconds();
            for (int i = 0; i < PREFETCH_COUNT / 2; i++) {
                var key =
                        new WaveformSegmentCache.SegmentKey(
                                forwardStart + i * segmentDuration,
                                viewport.pixelsPerSecond(),
                                viewport.viewportHeightPx());
                if (cache.get(key) == null) {
                    CompletableFuture<Image> future = renderSegment(key);
                    cache.put(key, future);
                }
            }
        }
    }

    /** Render single 200px segment. */
    private CompletableFuture<Image> renderSegment(WaveformSegmentCache.SegmentKey key) {
        return CompletableFuture.supplyAsync(
                () -> {
                    // Check for cancellation
                    if (Thread.currentThread().isInterrupted()) {
                        return null;
                    }

                    BufferedImage image =
                            new BufferedImage(
                                    SEGMENT_WIDTH_PX, key.height(), BufferedImage.TYPE_INT_ARGB);

                    Graphics2D g = image.createGraphics();
                    try {
                        // Enable antialiasing
                        g.setRenderingHint(
                                RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                        // Fill background
                        g.setColor(Color.WHITE);
                        g.fillRect(0, 0, SEGMENT_WIDTH_PX, key.height());

                        // TODO: Read audio samples for time range [key.startTime(), key.endTime()]
                        // TODO: Calculate min/max or RMS values
                        // TODO: Draw waveform visualization

                        // Placeholder: Draw a simple waveform pattern
                        g.setColor(Color.BLACK);
                        g.setStroke(new BasicStroke(1.0f));
                        int centerY = key.height() / 2;

                        // Draw center line
                        g.drawLine(0, centerY, SEGMENT_WIDTH_PX, centerY);

                        // Placeholder waveform (will be replaced with actual audio data)
                        g.setColor(new Color(0, 100, 200));
                        for (int x = 0; x < SEGMENT_WIDTH_PX; x++) {
                            if (Thread.currentThread().isInterrupted()) {
                                return null;
                            }
                            // Placeholder: sine wave for testing
                            double phase = (key.startTime() * 10 + x * 0.1);
                            int amplitude = (int) (Math.sin(phase) * key.height() * 0.3);
                            g.drawLine(x, centerY - amplitude, x, centerY + amplitude);
                        }

                    } finally {
                        g.dispose();
                    }

                    return image;
                },
                renderPool);
    }

    /** Composite segments into single viewport image. */
    private Image compositeSegments(List<Image> segments, ViewportContext viewport) {
        if (segments.isEmpty()) {
            return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        }

        BufferedImage composite =
                new BufferedImage(
                        viewport.viewportWidthPx(),
                        viewport.viewportHeightPx(),
                        BufferedImage.TYPE_INT_ARGB);

        Graphics2D g = composite.createGraphics();
        try {
            // Fill background
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, viewport.viewportWidthPx(), viewport.viewportHeightPx());

            // Draw each segment at its position
            int x = 0;
            for (Image segment : segments) {
                if (segment != null) {
                    g.drawImage(segment, x, 0, null);
                    x += SEGMENT_WIDTH_PX;

                    // Stop if we've filled the viewport
                    if (x >= viewport.viewportWidthPx()) {
                        break;
                    }
                }
            }

            // Handle partial last segment
            if (x < viewport.viewportWidthPx() && !segments.isEmpty()) {
                // Last segment might extend beyond viewport
                Image lastSegment = segments.get(segments.size() - 1);
                if (lastSegment != null) {
                    int remainingWidth = viewport.viewportWidthPx() - x + SEGMENT_WIDTH_PX;
                    g.setClip(x - SEGMENT_WIDTH_PX, 0, remainingWidth, viewport.viewportHeightPx());
                    g.drawImage(lastSegment, x - SEGMENT_WIDTH_PX, 0, null);
                }
            }
        } finally {
            g.dispose();
        }

        return composite;
    }
}
