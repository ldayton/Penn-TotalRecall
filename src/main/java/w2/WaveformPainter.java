package w2;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.swing.Timer;
import lombok.NonNull;

/**
 * Paints waveform display components with configurable refresh rate. Handles rendering of waveform
 * image, playback cursor, and status messages.
 */
public class WaveformPainter {

    private static final int FPS = 30;
    private final Timer repaintTimer;
    private volatile WaveformViewport viewport;
    private volatile WaveformPaintingDataSource dataSource;
    private volatile boolean needsRepaint;

    /** Create a painter without a viewport. Viewport must be set before painting operations. */
    public WaveformPainter() {
        this.viewport = null;
        this.dataSource = null;
        this.needsRepaint = false;
        this.repaintTimer = new Timer(1000 / FPS, e -> repaintIfNeeded());
    }

    /** Set the viewport to paint to. */
    public void setViewport(WaveformViewport viewport) {
        this.viewport = viewport;
    }

    /** Set the data source for painting information. */
    public void setDataSource(WaveformPaintingDataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Request a repaint on the next timer tick. */
    public void requestRepaint() {
        needsRepaint = true;
    }

    /** Start the repaint timer. */
    public void start() {
        repaintTimer.start();
    }

    /** Stop the repaint timer. */
    public void stop() {
        repaintTimer.stop();
    }

    /** Check if timer is running. */
    public boolean isRunning() {
        return repaintTimer.isRunning();
    }

    private void repaintIfNeeded() {
        if (needsRepaint && viewport != null && viewport.isVisible()) {
            needsRepaint = false;
            viewport.repaint();
        }
    }

    /**
     * Called by the viewport during its paint cycle to suggest painting. The painter decides
     * whether and what to paint.
     */
    public void suggestPaint() {
        if (viewport == null) {
            System.out.println("WaveformPainter: No viewport set");
            return;
        }
        if (dataSource == null) {
            System.out.println("WaveformPainter: No data source set");
            return;
        }

        Graphics2D g = viewport.getPaintGraphics();
        if (g == null) {
            System.out.println("WaveformPainter: No graphics context");
            return;
        }

        paint(g);
    }

    /**
     * Main paint orchestration method. Queries data sources, constructs ViewportContext, gets
     * rendered image, and paints.
     *
     * @param g Graphics context from the viewport
     */
    private void paint(@NonNull Graphics2D g) {
        if (viewport == null || dataSource == null) {
            return;
        }

        Rectangle bounds = viewport.getBounds();
        System.out.println("WaveformPainter: Bounds = " + bounds);

        TimeRange timeRange = dataSource.getTimeRange();

        if (timeRange == null) {
            // No audio loaded
            System.out.println("WaveformPainter: No time range, painting empty state");
            paintEmptyState(g, bounds);
            return;
        }
        System.out.println("WaveformPainter: Time range = " + timeRange);

        // Construct ViewportContext from the pieces
        ViewportContext context =
                new ViewportContext(
                        timeRange.startSeconds(),
                        timeRange.endSeconds(),
                        bounds.width,
                        bounds.height,
                        dataSource.getPixelsPerSecond(),
                        ViewportContext.ScrollDirection.FORWARD // TODO: track scroll direction
                        );

        // Get the waveform
        Waveform waveform = dataSource.getWaveform();
        if (waveform == null) {
            System.out.println("WaveformPainter: No waveform available");
            paintLoadingIndicator(g, bounds);
            return;
        }
        System.out.println("WaveformPainter: Got waveform, attempting to render");

        // Get rendered waveform image
        try {
            CompletableFuture<Image> imageFuture = waveform.renderViewport(context);

            // Try to get the image with a short timeout for responsiveness
            Image waveformImage = imageFuture.get(100, TimeUnit.MILLISECONDS);

            if (waveformImage != null) {
                System.out.println("WaveformPainter: Successfully got waveform image, painting it");
                paintWaveform(g, bounds, waveformImage);
            } else {
                System.out.println("WaveformPainter: Waveform image was null");
                clearBackground(g, bounds);
            }
        } catch (TimeoutException e) {
            // Still rendering, show loading indicator
            System.out.println("WaveformPainter: Timed out waiting for waveform render");
            paintLoadingIndicator(g, bounds);

            // Request a repaint when the rendering completes
            CompletableFuture<Image> imageFuture = waveform.renderViewport(context);
            imageFuture.whenComplete(
                    (image, ex) -> {
                        if (image != null) {
                            System.out.println(
                                    "WaveformPainter: Waveform rendering completed, requesting"
                                            + " repaint");
                            requestRepaint();
                        }
                    });
        } catch (Exception e) {
            // Error rendering
            System.out.println("WaveformPainter: Exception rendering waveform: " + e.getMessage());
            e.printStackTrace();
            clearBackground(g, bounds);
        }

        // Paint cursor on top if playing
        if (dataSource.isPlaying()) {
            paintPlaybackCursor(
                    g,
                    bounds,
                    timeRange,
                    dataSource.getPixelsPerSecond(),
                    dataSource.getPlaybackPositionSeconds());
        }
    }

    /**
     * Paint the waveform image within the given bounds.
     *
     * @param g Graphics context
     * @param bounds Area to paint in
     * @param waveformImage The rendered waveform image from w2
     */
    public void paintWaveform(
            @NonNull Graphics2D g, @NonNull Rectangle bounds, @NonNull Image waveformImage) {
        g.drawImage(waveformImage, bounds.x, bounds.y, bounds.width, bounds.height, null);
    }

    /**
     * Paint the playback cursor at the current position.
     *
     * @param g Graphics context
     * @param bounds Area of the waveform
     * @param timeRange Current time range being displayed
     * @param pixelsPerSecond Zoom level
     * @param playbackSeconds Current playback position in seconds
     */
    public void paintPlaybackCursor(
            @NonNull Graphics2D g,
            @NonNull Rectangle bounds,
            @NonNull TimeRange timeRange,
            int pixelsPerSecond,
            double playbackSeconds) {
        // Calculate x position from playback time and time range
        double relativeTime = playbackSeconds - timeRange.startSeconds();
        int cursorX = bounds.x + (int) (relativeTime * pixelsPerSecond);

        // Draw vertical cursor line if within bounds
        if (cursorX >= bounds.x && cursorX <= bounds.x + bounds.width) {
            g.setColor(Color.RED);
            g.drawLine(cursorX, bounds.y, cursorX, bounds.y + bounds.height);
        }
    }

    /**
     * Paint loading indicator while audio is being loaded.
     *
     * @param g Graphics context
     * @param bounds Area to paint in
     */
    public void paintLoadingIndicator(@NonNull Graphics2D g, @NonNull Rectangle bounds) {
        g.setColor(Color.GRAY);
        String message = "Loading...";
        int textWidth = g.getFontMetrics().stringWidth(message);
        int textHeight = g.getFontMetrics().getHeight();
        int x = bounds.x + (bounds.width - textWidth) / 2;
        int y = bounds.y + (bounds.height + textHeight) / 2;
        g.drawString(message, x, y);
    }

    /**
     * Paint error message when audio fails to load.
     *
     * @param g Graphics context
     * @param bounds Area to paint in
     * @param errorMessage The error message to display
     */
    public void paintErrorMessage(
            @NonNull Graphics2D g, @NonNull Rectangle bounds, @NonNull String errorMessage) {
        g.setColor(Color.RED);
        int textHeight = g.getFontMetrics().getHeight();
        int x = bounds.x + 10;
        int y = bounds.y + (bounds.height + textHeight) / 2;
        g.drawString("Error: " + errorMessage, x, y);
    }

    /**
     * Paint empty state when no audio is loaded.
     *
     * @param g Graphics context
     * @param bounds Area to paint in
     */
    public void paintEmptyState(@NonNull Graphics2D g, @NonNull Rectangle bounds) {
        g.setColor(Color.GRAY);
        String message = "No audio loaded";
        int textWidth = g.getFontMetrics().stringWidth(message);
        int textHeight = g.getFontMetrics().getHeight();
        int x = bounds.x + (bounds.width - textWidth) / 2;
        int y = bounds.y + (bounds.height + textHeight) / 2;
        g.drawString(message, x, y);
    }

    /**
     * Clear the background before painting.
     *
     * @param g Graphics context
     * @param bounds Area to clear
     */
    public void clearBackground(@NonNull Graphics2D g, @NonNull Rectangle bounds) {
        g.setColor(Color.WHITE);
        g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
    }
}
