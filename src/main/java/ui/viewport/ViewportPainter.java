package ui.viewport;

import core.viewport.ViewportPaintingDataSource;
import core.viewport.ViewportPaintingDataSource.ViewportRenderSpec;
import core.waveform.ScreenDimension;
import core.waveform.WaveformViewport;
import jakarta.inject.Inject;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Paints waveform display components with configurable refresh rate. Handles rendering of waveform
 * image, playback cursor, and status messages.
 */
@Slf4j
public final class ViewportPainter {

    private static final int FPS = 60;
    private static final int RENDER_TIMEOUT_MS = 750;
    private final Timer repaintTimer;
    private volatile WaveformViewport viewport;
    private volatile ViewportPaintingDataSource dataSource;

    /** Create a painter with dependency injection. */
    @Inject
    public ViewportPainter() {
        this.viewport = null;
        this.dataSource = null;
        this.repaintTimer =
                new Timer(
                        1000 / FPS,
                        _ -> {
                            if (viewport != null && viewport.isVisible()) {
                                viewport.repaint();
                            }
                        });
    }

    /** Set the viewport to paint to. */
    public void setViewport(WaveformViewport viewport) {
        this.viewport = viewport;
    }

    /** Set the data source for painting information. */
    public void setDataSource(ViewportPaintingDataSource dataSource) {
        this.dataSource = dataSource;
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

    /** Suggest a paint during the viewport's paint cycle. */
    public void suggestPaint() {
        if (viewport == null) {
            return;
        }
        if (dataSource == null) {
            return;
        }

        Graphics2D g = viewport.getPaintGraphics();
        if (g == null) {
            return;
        }

        paint(g);
    }

    /** Main paint orchestration: query data source, render, and draw. */
    private void paint(@NonNull Graphics2D g) {
        if (viewport == null || dataSource == null) {
            return;
        }
        ScreenDimension bounds = viewport.getViewportBounds();
        ViewportRenderSpec ctx = dataSource.getRenderSpec(bounds);
        switch (ctx.mode()) {
            case EMPTY -> {
                clearBackground(g, bounds);
                paintEmptyState(g, bounds);
            }
            case LOADING -> {
                clearBackground(g, bounds);
                paintLoadingIndicator(g, bounds);
            }
            case ERROR -> {
                clearBackground(g, bounds);
                paintErrorMessage(g, bounds, ctx.errorMessage().orElse("Audio loading failed"));
            }
            case RENDER -> {
                long id = ctx.generation();
                var future = ctx.image();
                if (future.isDone()) {
                    try {
                        Image image = future.getNow(null);
                        if (image != null) {
                            paintWaveform(
                                    g,
                                    bounds.x(),
                                    bounds.y(),
                                    bounds.width(),
                                    bounds.height(),
                                    image);
                            // Only draw reference line and playhead when waveform is ready
                            paintReferenceLine(g, bounds);
                            paintPlayhead(g, bounds);
                        } else {
                            clearBackground(g, bounds);
                        }
                    } catch (Exception e) {
                        clearBackground(g, bounds);
                    }
                } else {
                    clearBackground(g, bounds);
                    // Non-blocking: schedule a repaint when rendering completes (with timeout)
                    // completeOnTimeout will cancel the original future if it times out
                    var repaintFuture = future.orTimeout(RENDER_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    repaintFuture.whenComplete(
                            (img, ex) -> {
                                if (viewport != null && viewport.isVisible()) {
                                    SwingUtilities.invokeLater(
                                            () -> {
                                                // Prevent out-of-order renders by verifying
                                                // generation id
                                                ViewportRenderSpec latest =
                                                        dataSource.getRenderSpec(
                                                                viewport.getViewportBounds());
                                                if (latest.generation() == id) {
                                                    viewport.repaint();
                                                } else {
                                                    log.debug(
                                                            "Discarding stale render completion:"
                                                                    + " staleId={}, latestId={}",
                                                            id,
                                                            latest.generation());
                                                }
                                                if (ex != null) {
                                                    // Check if it's a timeout or cancellation
                                                    if (ex
                                                            instanceof
                                                            java.util.concurrent.TimeoutException) {
                                                        log.debug(
                                                                "Waveform render timed out after"
                                                                        + " {}ms",
                                                                RENDER_TIMEOUT_MS);
                                                    } else if (ex.getCause()
                                                            instanceof
                                                            java.util.concurrent
                                                                    .CancellationException) {
                                                        log.debug("Waveform render was cancelled");
                                                    } else {
                                                        log.warn(
                                                                "Waveform render completed"
                                                                        + " exceptionally: {}",
                                                                ex.toString());
                                                    }
                                                }
                                            });
                                }
                            });
                }
            }
        }
    }

    /** Paint the waveform image within the given bounds. */
    public void paintWaveform(
            @NonNull Graphics2D g,
            int x,
            int y,
            int width,
            int height,
            @NonNull Image waveformImage) {
        g.drawImage(waveformImage, x, y, width, height, null);
    }

    /** Paint loading indicator while audio is being loaded. */
    public void paintLoadingIndicator(@NonNull Graphics2D g, @NonNull ScreenDimension bounds) {
        g.setColor(Color.GRAY);
        String message = "Loading...";
        int textWidth = g.getFontMetrics().stringWidth(message);
        int textHeight = g.getFontMetrics().getHeight();
        int x = bounds.x() + (bounds.width() - textWidth) / 2;
        int y = bounds.y() + (bounds.height() + textHeight) / 2;
        g.drawString(message, x, y);
    }

    /** Paint error message when audio fails to load. */
    public void paintErrorMessage(
            @NonNull Graphics2D g, @NonNull ScreenDimension bounds, @NonNull String errorMessage) {
        g.setColor(Color.RED);
        int textHeight = g.getFontMetrics().getHeight();
        int x = bounds.x() + 10;
        int y = bounds.y() + (bounds.height() + textHeight) / 2;
        g.drawString("Error: " + errorMessage, x, y);
    }

    /** Paint empty state when no audio is loaded. */
    public void paintEmptyState(@NonNull Graphics2D g, @NonNull ScreenDimension bounds) {
        g.setColor(Color.GRAY);
        String message = "No audio loaded";
        int textWidth = g.getFontMetrics().stringWidth(message);
        int textHeight = g.getFontMetrics().getHeight();
        int x = bounds.x() + (bounds.width() - textWidth) / 2;
        int y = bounds.y() + (bounds.height() + textHeight) / 2;
        g.drawString(message, x, y);
    }

    /** Clear the background before painting. */
    public void clearBackground(@NonNull Graphics2D g, @NonNull ScreenDimension bounds) {
        g.setColor(Color.WHITE);
        g.fillRect(bounds.x(), bounds.y(), bounds.width(), bounds.height());
    }

    /** Paint a horizontal reference line across the viewport. */
    public void paintReferenceLine(@NonNull Graphics2D g, @NonNull ScreenDimension bounds) {
        g.setPaintMode();
        g.setColor(Color.BLACK);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        // Draw horizontal line across entire viewport width at center
        g.fillRect(bounds.x(), bounds.y() + bounds.height() / 2, bounds.width(), 1);
    }

    /** Paint the playhead at the center of the viewport. */
    public void paintPlayhead(@NonNull Graphics2D g, @NonNull ScreenDimension bounds) {
        // Playhead is always at exactly 50% of viewport width
        int playheadX = bounds.x() + bounds.width() / 2;

        // Use XOR mode for the playhead
        g.setXORMode(Color.WHITE);
        g.setColor(Color.BLACK);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.fillRect(playheadX, bounds.y(), 1, bounds.height());
        g.setPaintMode(); // Reset to normal paint mode
    }
}
