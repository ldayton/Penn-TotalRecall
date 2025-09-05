package s2;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import javax.swing.Timer;
import lombok.NonNull;
import w2.ViewportContext;

/**
 * Paints waveform display components with configurable refresh rate. Handles rendering of waveform
 * image, playback cursor, and status messages.
 */
class WaveformPainter {

    private static final int FPS = 30;
    private final Timer repaintTimer;
    private final WaveformViewport viewport;
    private volatile boolean needsRepaint;

    /**
     * Create a painter for the given viewport.
     *
     * @param viewport The viewport to repaint
     */
    public WaveformPainter(@NonNull WaveformViewport viewport) {
        this.viewport = viewport;
        this.needsRepaint = false;
        this.repaintTimer = new Timer(1000 / FPS, e -> repaintIfNeeded());
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
        if (needsRepaint && viewport.isVisible()) {
            needsRepaint = false;
            viewport.repaint();
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
     * @param viewport Current viewport context
     * @param playbackSeconds Current playback position in seconds
     */
    public void paintPlaybackCursor(
            @NonNull Graphics2D g,
            @NonNull Rectangle bounds,
            @NonNull ViewportContext viewport,
            double playbackSeconds) {
        // Calculate x position from playback time and viewport
        double pixelsPerSecond =
                bounds.width / (viewport.endTimeSeconds() - viewport.startTimeSeconds());
        double relativeTime = playbackSeconds - viewport.startTimeSeconds();
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
