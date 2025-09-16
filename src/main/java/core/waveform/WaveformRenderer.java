package core.waveform;

import core.audio.AudioMetadata;
import core.audio.SampleReader;
import core.waveform.signal.PixelScaler;
import core.waveform.signal.WaveformProcessor;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Priority-based waveform renderer with prefetch strategy. Fills segment cache efficiently using
 * industry-standard approaches.
 */
class WaveformRenderer {
    private static final Logger logger = LoggerFactory.getLogger(WaveformRenderer.class);

    private static final int SEGMENT_WIDTH_PX = WaveformSegmentCache.SEGMENT_WIDTH_PX;
    private static final int PREFETCH_COUNT = WaveformSegmentCache.PREFETCH_COUNT;

    // Waveform rendering constants (from original WaveformRenderer)
    private static final Color WAVEFORM_BACKGROUND = Color.WHITE;
    private static final Color WAVEFORM_REFERENCE_LINE = Color.BLACK;
    private static final Color WAVEFORM_SCALE_LINE = new Color(226, 224, 131);
    private static final Color WAVEFORM_SCALE_TEXT = Color.BLACK;
    private static final Color FIRST_CHANNEL_WAVEFORM = Color.BLACK;
    private static final DecimalFormat SEC_FORMAT = new DecimalFormat("0.00s");
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
    private final WaveformPeakDetector peakDetector;

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
            @NonNull SampleReader sampleReader,
            int sampleRate,
            @NonNull AudioMetadata metadata) {
        this.audioFilePath = audioFilePath;
        this.cache = cache;
        this.renderPool = renderPool;
        this.processor = new WaveformProcessor(sampleReader, sampleRate, new PixelScaler());
        this.peakDetector = new WaveformPeakDetector(audioFilePath, processor, metadata);

        // Initialize the peak detector asynchronously to pre-calculate common resolutions
        CompletableFuture.runAsync(
                () -> {
                    try {
                        peakDetector.initialize();
                    } catch (Exception e) {
                        logger.error("Failed to initialize peak detector: {}", e.getMessage());
                    }
                },
                renderPool);
    }

    /** Fill cache for viewport with priority-based rendering. */
    CompletableFuture<Image> renderViewport(@NonNull WaveformViewportSpec viewport) {
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
                            Image compositeImage = compositeSegments(segments, viewport);
                            logger.trace(
                                    "Successfully rendered viewport [{}s - {}s] with {} segments",
                                    String.format("%.2f", viewport.startTimeSeconds()),
                                    String.format("%.2f", viewport.endTimeSeconds()),
                                    segments.size());
                            return compositeImage;
                        })
                .exceptionally(
                        e -> {
                            // Check if this is a cancellation
                            Throwable cause = e.getCause();
                            if (cause instanceof java.util.concurrent.CancellationException) {
                                // Don't log cancellations - they're expected during navigation
                                logger.debug(
                                        "Waveform render cancelled for viewport at {}s",
                                        viewport.startTimeSeconds());
                            } else {
                                // Log other errors
                                logger.warn("Error rendering waveform viewport: ", e);
                            }
                            return null;
                        });
    }

    /** Calculate which segments are needed for the viewport. */
    private List<WaveformSegmentCache.SegmentKey> calculateVisibleSegments(
            @NonNull WaveformViewportSpec viewport) {
        List<WaveformSegmentCache.SegmentKey> segments = new ArrayList<>();

        // Calculate segment indices based on viewport time range
        // Each segment represents SEGMENT_WIDTH_PX pixels worth of audio
        long startIndex =
                (long)
                        Math.floor(
                                viewport.startTimeSeconds()
                                        * viewport.pixelsPerSecond()
                                        / SEGMENT_WIDTH_PX);
        long endIndex =
                (long)
                        Math.ceil(
                                viewport.endTimeSeconds()
                                        * viewport.pixelsPerSecond()
                                        / SEGMENT_WIDTH_PX);

        logger.trace(
                "calculateVisibleSegments: viewport=[{}-{}s] @ {}px/s, HEIGHT={},"
                        + " segmentIndices=[{}-{}]",
                viewport.startTimeSeconds(),
                viewport.endTimeSeconds(),
                viewport.pixelsPerSecond(),
                viewport.viewportHeightPx(),
                startIndex,
                endIndex);

        // Generate segment keys for the index range
        for (long index = startIndex; index <= endIndex; index++) {
            segments.add(
                    new WaveformSegmentCache.SegmentKey(
                            index, viewport.pixelsPerSecond(), viewport.viewportHeightPx()));
        }

        return segments;
    }

    /** Add symmetric prefetch tasks (no scroll direction). */
    private void addPrefetchTasks(@NonNull WaveformViewportSpec viewport) {
        // Check for interruption before starting prefetch
        if (Thread.currentThread().isInterrupted()) {
            return; // Skip prefetch if interrupted
        }

        // Calculate the last visible segment index
        long endIndex =
                (long)
                        Math.ceil(
                                viewport.endTimeSeconds()
                                        * viewport.pixelsPerSecond()
                                        / SEGMENT_WIDTH_PX);
        long startIndex =
                (long)
                        Math.floor(
                                viewport.startTimeSeconds()
                                        * viewport.pixelsPerSecond()
                                        / SEGMENT_WIDTH_PX);

        logger.trace(
                "Prefetching for viewport {}s-{}s, segments {}-{}, will prefetch up to segment {}",
                viewport.startTimeSeconds(),
                viewport.endTimeSeconds(),
                startIndex,
                endIndex,
                endIndex + PREFETCH_COUNT);

        // Prefetch forward (segments after the viewport)
        for (int i = 1; i <= PREFETCH_COUNT; i++) {
            long prefetchIndex = endIndex + i;
            var key =
                    new WaveformSegmentCache.SegmentKey(
                            prefetchIndex, viewport.pixelsPerSecond(), viewport.viewportHeightPx());
            // Use no-stats version for prefetch checks
            if (cache.get(key, false) == null) {
                CompletableFuture<Image> future = renderSegment(key);
                cache.put(key, future);
            }
        }

        // Prefetch backward (segments before the viewport)
        for (int i = 1; i <= PREFETCH_COUNT; i++) {
            long prefetchIndex = startIndex - i;
            // Don't prefetch segments before the start of the audio
            if (prefetchIndex < 0) {
                continue;
            }
            var key =
                    new WaveformSegmentCache.SegmentKey(
                            prefetchIndex, viewport.pixelsPerSecond(), viewport.viewportHeightPx());
            // Use no-stats version for prefetch checks
            if (cache.get(key, false) == null) {
                CompletableFuture<Image> future = renderSegment(key);
                cache.put(key, future);
            }
        }
    }

    /** Render single 200px segment. */
    private CompletableFuture<Image> renderSegment(@NonNull WaveformSegmentCache.SegmentKey key) {

        // For segments that start before 0, we'll render partial content
        // Calculate the segment duration
        double segmentDuration = key.duration();

        // If segment ends before 0, return empty segment
        if (key.startTime() + segmentDuration <= 0) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.supplyAsync(
                () -> {
                    // Check for cancellation at start
                    if (Thread.currentThread().isInterrupted()) {
                        throw new java.util.concurrent.CancellationException("Render cancelled");
                    }

                    logger.trace(
                            "renderSegment: Creating image for segment {} with HEIGHT={}",
                            key.segmentIndex(),
                            key.height());
                    BufferedImage image =
                            new BufferedImage(
                                    SEGMENT_WIDTH_PX, key.height(), BufferedImage.TYPE_INT_RGB);

                    // Handle segments that start before 0
                    int chunkIndex = 0;
                    double segmentOffsetInChunk = 0;

                    if (key.startTime() >= 0) {
                        chunkIndex =
                                (int)
                                        (key.startTime()
                                                / WaveformProcessor
                                                        .STANDARD_CHUNK_DURATION_SECONDS);
                        double chunkStartTime =
                                chunkIndex * WaveformProcessor.STANDARD_CHUNK_DURATION_SECONDS;
                        segmentOffsetInChunk = key.startTime() - chunkStartTime;
                    } else {
                        // Segment starts before 0, we'll use chunk 0 but offset differently
                        chunkIndex = 0;
                        segmentOffsetInChunk = key.startTime(); // Will be negative
                    }

                    // Check for cancellation before expensive operation
                    if (Thread.currentThread().isInterrupted()) {
                        throw new java.util.concurrent.CancellationException("Render cancelled");
                    }

                    // Process the full 10-second chunk (matching original implementation)
                    double[] tempChunkData;
                    try {
                        int chunkWidthPixels =
                                (int)
                                        (WaveformProcessor.STANDARD_CHUNK_DURATION_SECONDS
                                                * key.pixelsPerSecond());
                        tempChunkData =
                                processor.processAudioForDisplay(
                                        audioFilePath, chunkIndex, chunkWidthPixels);
                    } catch (Exception e) {
                        logger.warn(
                                "Failed to process audio for segment at {}s: {}",
                                key.startTime(),
                                e.getMessage());
                        tempChunkData = new double[0];
                    }
                    final double[] fullChunkData = tempChunkData;

                    // Extract the 200px segment we need from the full chunk
                    double[] valsToDraw = new double[SEGMENT_WIDTH_PX];
                    int segmentStartPixel = (int) (segmentOffsetInChunk * key.pixelsPerSecond());

                    // Handle segments that start before 0
                    for (int i = 0; i < SEGMENT_WIDTH_PX; i++) {
                        int dataIndex = segmentStartPixel + i;
                        if (dataIndex >= 0 && dataIndex < fullChunkData.length) {
                            valsToDraw[i] = fullChunkData[dataIndex];
                        }
                        // else leave as 0 (silence for out-of-bounds regions)
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
                        double peak = peakDetector.getPeak(key.pixelsPerSecond());

                        double yScale;
                        if (peak <= 0) {
                            yScale = 0;
                        } else {
                            yScale = ((key.height() / 2) - 1) / peak;
                            if (Double.isInfinite(yScale) || Double.isNaN(yScale)) {
                                yScale = 0;
                            }
                        }

                        logger.trace(
                                "Segment {} render scaling: height={}, centerY={}, peak={},"
                                        + " yScale={}",
                                key.segmentIndex(),
                                key.height(),
                                centerY,
                                peak,
                                yScale);

                        // Draw waveform
                        for (int i = 0; i < valsToDraw.length && i < SEGMENT_WIDTH_PX; i++) {
                            // Check for interruption periodically (every 10 pixels for performance)
                            if (i % 10 == 0 && Thread.currentThread().isInterrupted()) {
                                g.dispose();
                                throw new java.util.concurrent.CancellationException(
                                        "Render cancelled during draw");
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

                    logger.trace(
                            "Successfully rendered segment at {}s ({}x{} px)",
                            key.startTime(),
                            SEGMENT_WIDTH_PX,
                            key.height());
                    return image;
                },
                renderPool);
    }

    /** Composite segments into single viewport image. */
    private Image compositeSegments(
            @NonNull List<Image> segments, @NonNull WaveformViewportSpec viewport) {
        // Check for interruption before compositing
        if (Thread.currentThread().isInterrupted()) {
            throw new java.util.concurrent.CancellationException("Composite cancelled");
        }

        if (segments.isEmpty()) {
            return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        }

        logger.trace(
                "Creating composite image: width={}, HEIGHT={}",
                viewport.viewportWidthPx(),
                viewport.viewportHeightPx());
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

            // Calculate the offset for the first segment
            // The first segment's index tells us its start time
            long firstSegmentIndex =
                    (long)
                            Math.floor(
                                    viewport.startTimeSeconds()
                                            * viewport.pixelsPerSecond()
                                            / SEGMENT_WIDTH_PX);

            // Calculate where in pixels the first segment should start
            // This will be negative if the viewport starts partway through the segment
            double firstSegmentStartTime =
                    (firstSegmentIndex * SEGMENT_WIDTH_PX) / (double) viewport.pixelsPerSecond();
            double offsetSeconds = firstSegmentStartTime - viewport.startTimeSeconds();
            int offsetPixels = (int) Math.round(offsetSeconds * viewport.pixelsPerSecond());

            logger.trace(
                    "Compositing: viewport=[{}-{}s], viewportHEIGHT={}, firstSegIdx={},"
                            + " segStartTime={}, offsetSec={}, offsetPx={}, segmentCount={}",
                    viewport.startTimeSeconds(),
                    viewport.endTimeSeconds(),
                    viewport.viewportHeightPx(),
                    firstSegmentIndex,
                    firstSegmentStartTime,
                    offsetSeconds,
                    offsetPixels,
                    segments.size());

            // Draw each segment at its position
            int x = offsetPixels;
            for (Image segment : segments) {
                // Check for interruption during compositing
                if (Thread.currentThread().isInterrupted()) {
                    throw new java.util.concurrent.CancellationException(
                            "Composite cancelled during draw");
                }

                if (segment != null) {
                    // Center the segment vertically if there's a height mismatch
                    int segmentHeight = segment.getHeight(null);
                    int y = (viewport.viewportHeightPx() - segmentHeight) / 2;
                    if (y != 0) {
                        logger.warn(
                                "HEIGHT MISMATCH: viewport height={}, segment height={}, y"
                                        + " offset={}",
                                viewport.viewportHeightPx(),
                                segmentHeight,
                                y);
                    }
                    g.drawImage(segment, x, y, null);
                }
                // Always advance position, even for null segments (pre-audio silence)
                x += SEGMENT_WIDTH_PX;

                // Stop if we've filled the viewport
                if (x >= viewport.viewportWidthPx()) {
                    break;
                }
            }

            // Handle partial last segment
            if (x < viewport.viewportWidthPx() && !segments.isEmpty()) {
                // Last segment might extend beyond viewport
                Image lastSegment = segments.get(segments.size() - 1);
                if (lastSegment != null) {
                    int segmentHeight = lastSegment.getHeight(null);
                    int y = (viewport.viewportHeightPx() - segmentHeight) / 2;
                    int remainingWidth = viewport.viewportWidthPx() - x + SEGMENT_WIDTH_PX;
                    g.setClip(x - SEGMENT_WIDTH_PX, 0, remainingWidth, viewport.viewportHeightPx());
                    g.drawImage(lastSegment, x - SEGMENT_WIDTH_PX, y, null);
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

        // Snap markers to absolute second boundaries (…, -2, -1, 0, 1, 2, …)
        // Find the first integer second that is >= startTimeSeconds, but include start if exact.
        final double epsilon = 1e-9;
        double startFloor = Math.floor(startTimeSeconds + epsilon);
        double firstSecond =
                (Math.abs(startTimeSeconds - startFloor) <= epsilon)
                        ? startFloor
                        : (startFloor + 1.0);

        // Pixel offset from the left edge to the first whole-second tick
        double offsetSeconds = firstSecond - startTimeSeconds;
        int x = (int) Math.round(offsetSeconds * pixelsPerSecond);

        double tickSecond = firstSecond;
        while (x < width) {
            g.setColor(WAVEFORM_SCALE_LINE);
            g.drawLine(x, 0, x, height - 1);

            g.setColor(WAVEFORM_SCALE_TEXT);
            g.drawString(SEC_FORMAT.format(tickSecond), x + 5, height - 5);

            x += pixelsPerSecond; // advance exactly 1s in pixels
            tickSecond += 1.0;
        }
    }
}
