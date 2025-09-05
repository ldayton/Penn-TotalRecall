package w2;

import a2.AudioEngine;
import a2.AudioHandle;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.ConcurrentHashMap;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import w2.signal.PixelScaler;
import w2.signal.WaveformProcessor;

/**
 * Priority-based waveform renderer with prefetch strategy. Fills segment cache efficiently using
 * industry-standard approaches.
 */
class WaveformRenderer {
    private static final Logger logger = LoggerFactory.getLogger(WaveformRenderer.class);

    private static final int SEGMENT_WIDTH_PX = 200;
    private static final int PREFETCH_COUNT = 2; // Segments to prefetch in each direction

    // Waveform rendering constants (from original WaveformRenderer)
    private static final Color WAVEFORM_BACKGROUND = Color.WHITE;
    private static final Color WAVEFORM_REFERENCE_LINE = Color.BLACK;
    private static final Color WAVEFORM_SCALE_LINE = new Color(226, 224, 131);
    private static final Color WAVEFORM_SCALE_TEXT = Color.BLACK;
    private static final Color FIRST_CHANNEL_WAVEFORM = Color.BLACK;
    private static final java.text.DecimalFormat SEC_FORMAT = new java.text.DecimalFormat("0.00s");
    private static final RenderingHints RENDERING_HINTS =
            new RenderingHints(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    static {
        RENDERING_HINTS.put(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        RENDERING_HINTS.put(
                RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        RENDERING_HINTS.put(
                RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }

    private final WaveformSegmentCache cache;
    private final ExecutorService renderPool;
    private final String audioFilePath;
    private final WaveformProcessor processor;

    // Cache global peak per resolution for consistent scaling
    private final ConcurrentHashMap<Integer, Double> resolutionPeaks = new ConcurrentHashMap<>();

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

    record RenderTask(@NonNull WaveformSegmentCache.SegmentKey key, @NonNull Priority priority)
            implements Comparable<RenderTask> {

        @Override
        public int compareTo(@NonNull RenderTask other) {
            return Integer.compare(this.priority.getValue(), other.priority.getValue());
        }
    }

    WaveformRenderer(
            @NonNull String audioFilePath,
            @NonNull WaveformSegmentCache cache,
            @NonNull ExecutorService renderPool,
            @NonNull AudioEngine audioEngine,
            @NonNull AudioHandle audioHandle,
            int sampleRate) {
        this.audioFilePath = audioFilePath;
        this.cache = cache;
        this.renderPool = renderPool;
        this.processor = new WaveformProcessor(audioEngine, audioHandle, new PixelScaler());
    }

    /** Fill cache for viewport with priority-based rendering. */
    CompletableFuture<Image> renderViewport(@NonNull ViewportContext viewport) {
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
                        _ -> {
                            List<Image> segments =
                                    segmentFutures.stream().map(f -> f.getNow(null)).toList();
                            return compositeSegments(segments, viewport);
                        });
    }

    /** Calculate which segments are needed for the viewport. */
    private List<WaveformSegmentCache.SegmentKey> calculateVisibleSegments(
            @NonNull ViewportContext viewport) {
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
    private void addPrefetchTasks(@NonNull ViewportContext viewport) {
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
    private CompletableFuture<Image> renderSegment(@NonNull WaveformSegmentCache.SegmentKey key) {
        return CompletableFuture.supplyAsync(
                () -> {
                    // Check for cancellation
                    if (Thread.currentThread().isInterrupted()) {
                        return null;
                    }

                    BufferedImage image =
                            new BufferedImage(
                                    SEGMENT_WIDTH_PX, key.height(), BufferedImage.TYPE_INT_RGB);

                    // Calculate which 10-second chunk this segment belongs to
                    final double CHUNK_DURATION_SECONDS = 10.0;
                    final double PRE_DATA_SECONDS = 0.25; // Match original overlap
                    int chunkIndex = (int) (key.startTime() / CHUNK_DURATION_SECONDS);

                    // Calculate position of this segment within the chunk
                    double chunkStartTime = chunkIndex * CHUNK_DURATION_SECONDS;
                    double segmentOffsetInChunk = key.startTime() - chunkStartTime;

                    // Process the full 10-second chunk (matching original implementation)
                    double[] tempChunkData;
                    try {
                        int chunkWidthPixels =
                                (int) (CHUNK_DURATION_SECONDS * key.pixelsPerSecond());
                        tempChunkData =
                                processor.processAudioForDisplay(
                                        audioFilePath,
                                        chunkIndex,
                                        CHUNK_DURATION_SECONDS,
                                        PRE_DATA_SECONDS, // Match original overlap
                                        0.001, // Min frequency for BandPassFilter
                                        0.45, // Max frequency matching original
                                        chunkWidthPixels);
                    } catch (Exception e) {
                        logger.warn(
                                "Failed to process audio for segment at {}s: {}",
                                key.startTime(),
                                e.getMessage());
                        tempChunkData = new double[0];
                    }
                    final double[] fullChunkData = tempChunkData;

                    // Initialize global peak on first chunk if not already set
                    resolutionPeaks.computeIfAbsent(
                            key.pixelsPerSecond(),
                            pps -> {
                                double p = getRenderingPeak(fullChunkData, Math.max(1, pps / 2));
                                // logger.debug("Initialized rendering peak: {} for {} px/s", p,
                                // pps);
                                return p;
                            });

                    // Extract the 200px segment we need from the full chunk
                    double[] valsToDraw = new double[SEGMENT_WIDTH_PX];
                    int segmentStartPixel = (int) (segmentOffsetInChunk * key.pixelsPerSecond());
                    for (int i = 0;
                            i < SEGMENT_WIDTH_PX && segmentStartPixel + i < fullChunkData.length;
                            i++) {
                        valsToDraw[i] = fullChunkData[segmentStartPixel + i];
                    }

                    Graphics2D g = image.createGraphics();
                    try {
                        // Enable antialiasing
                        g.setRenderingHints(RENDERING_HINTS);

                        // Fill background
                        g.setColor(WAVEFORM_BACKGROUND);
                        g.fillRect(0, 0, SEGMENT_WIDTH_PX, key.height());

                        // Draw reference line
                        g.setColor(WAVEFORM_REFERENCE_LINE);
                        int centerY = key.height() / 2;
                        g.drawLine(0, centerY, SEGMENT_WIDTH_PX, centerY);

                        // Draw time scale (vertical lines and labels)
                        drawTimeScale(
                                g,
                                SEGMENT_WIDTH_PX,
                                key.height(),
                                key.startTime(),
                                key.pixelsPerSecond());

                        // Draw waveform using exact same logic as original
                        g.setColor(FIRST_CHANNEL_WAVEFORM);

                        // Use global peak for consistent scaling across all segments
                        double peak = resolutionPeaks.getOrDefault(key.pixelsPerSecond(), 0.0);

                        double yScale;
                        if (peak <= 0) {
                            yScale = 0;
                        } else {
                            yScale = ((key.height() / 2) - 1) / peak;
                            if (Double.isInfinite(yScale) || Double.isNaN(yScale)) {
                                yScale = 0;
                            }
                        }

                        // Draw waveform
                        for (int i = 0; i < valsToDraw.length && i < SEGMENT_WIDTH_PX; i++) {
                            if (Thread.currentThread().isInterrupted()) {
                                return null;
                            }

                            double scaledSample = valsToDraw[i] * yScale;
                            int topY = (int) (centerY - scaledSample);
                            int bottomY = (int) (centerY + scaledSample);

                            g.drawLine(i, centerY, i, topY);
                            g.drawLine(i, centerY, i, bottomY);
                        }

                    } finally {
                        g.dispose();
                    }

                    return image;
                },
                renderPool);
    }

    /** Composite segments into single viewport image. */
    private Image compositeSegments(
            @NonNull List<Image> segments, @NonNull ViewportContext viewport) {
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

    /** Draw time scale lines and labels - matches original WaveformRenderer */
    private void drawTimeScale(
            @NonNull Graphics2D g,
            int width,
            int height,
            double startTimeSeconds,
            int pixelsPerSecond) {
        if (pixelsPerSecond <= 0) {
            return;
        }

        for (int i = 0; i < width; i += pixelsPerSecond) {
            g.setColor(WAVEFORM_SCALE_LINE);
            g.drawLine(i, 0, i, height - 1);

            g.setColor(WAVEFORM_SCALE_TEXT);
            double seconds = startTimeSeconds + (i / (double) pixelsPerSecond);
            g.drawString(SEC_FORMAT.format(seconds), i + 5, height - 5);
        }
    }

    /** Calculate rendering peak using consecutive pixel minimum - matches original */
    private double getRenderingPeak(@NonNull double[] pixelValues, int skipInitialPixels) {
        if (pixelValues.length < skipInitialPixels + 2) {
            return 0;
        }

        double maxConsecutive = 0;
        for (int i = skipInitialPixels; i < pixelValues.length - 1; i++) {
            double consecutiveVals = Math.min(pixelValues[i], pixelValues[i + 1]);
            maxConsecutive = Math.max(consecutiveVals, maxConsecutive);
        }

        return maxConsecutive;
    }
}
