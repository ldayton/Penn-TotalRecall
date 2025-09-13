package ui.waveform;

import core.state.AudioSessionStateMachine;
import core.waveform.ScreenDimension;
import core.waveform.TimeRange;
import core.waveform.ViewportContext;
import core.waveform.Waveform;
import core.waveform.WaveformPaintingDataSource;
import core.waveform.WaveformViewport;
import jakarta.inject.Inject;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
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

    private static final int FPS = 60;
    private final Timer repaintTimer;
    private final AudioSessionStateMachine stateMachine;
    private volatile WaveformViewport viewport;
    private volatile WaveformPaintingDataSource dataSource;

    /** Create a painter with dependency injection. */
    @Inject
    public WaveformPainter(AudioSessionStateMachine stateMachine) {
        this.stateMachine = stateMachine;
        this.viewport = null;
        this.dataSource = null;
        this.repaintTimer =
                new Timer(
                        1000 / FPS,
                        _ -> {
                            if (viewport != null && viewport.isVisible()) {
                                // Prepare data for next frame if we have a WaveformPaintDataSource
                                if (dataSource != null
                                        && dataSource instanceof state.WaveformPaintDataSource) {
                                    state.WaveformPaintDataSource paintDataSource =
                                            (state.WaveformPaintDataSource) dataSource;
                                    // Update viewport width in case canvas was resized
                                    paintDataSource.updateViewportWidth(
                                            viewport.getViewportBounds().width());

                                    // Only prepare frame if audio is actually loaded
                                    if (stateMachine.isAudioLoaded()) {
                                        paintDataSource.prepareFrame();
                                    }
                                }
                                viewport.repaint();
                            }
                        });
    }

    /** Set the viewport to paint to. */
    public void setViewport(WaveformViewport viewport) {
        this.viewport = viewport;
    }

    /** Set the data source for painting information. */
    public void setDataSource(WaveformPaintingDataSource dataSource) {
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

    /**
     * Called by the viewport during its paint cycle to suggest painting. The painter decides
     * whether and what to paint.
     */
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

        ScreenDimension bounds = viewport.getViewportBounds();

        // Check state to determine what to paint
        AudioSessionStateMachine.State state = stateMachine.getCurrentState();

        if (state == AudioSessionStateMachine.State.NO_AUDIO) {
            paintEmptyState(g, bounds);
            return;
        }

        if (state == AudioSessionStateMachine.State.LOADING) {
            paintLoadingIndicator(g, bounds);
            return;
        }

        if (state == AudioSessionStateMachine.State.ERROR) {
            paintErrorMessage(g, bounds, "Audio loading failed");
            return;
        }

        // If we're in READY, PLAYING, or PAUSED, we should have audio
        TimeRange timeRange = dataSource.getTimeRange();
        if (timeRange == null) {
            // This shouldn't happen if state machine is correct
            return;
        }

        // Construct ViewportContext from the pieces
        ViewportContext context =
                new ViewportContext(
                        timeRange.startSeconds(),
                        timeRange.endSeconds(),
                        bounds.width(),
                        bounds.height(),
                        dataSource.getPixelsPerSecond());

        // Get the waveform
        Waveform waveform = dataSource.getWaveform();
        if (waveform == null) {
            paintLoadingIndicator(g, bounds);
            return;
        }

        // Get rendered waveform image
        try {
            CompletableFuture<Image> imageFuture = waveform.renderViewport(context);

            // Try to get the image with a short timeout for responsiveness
            Image waveformImage = imageFuture.get(100, TimeUnit.MILLISECONDS);

            if (waveformImage != null) {
                paintWaveform(g, bounds, waveformImage);
            } else {
                clearBackground(g, bounds);
            }
        } catch (TimeoutException e) {
            // Still rendering, show loading indicator
            paintLoadingIndicator(g, bounds);

            // Request a repaint when the rendering completes
            CompletableFuture<Image> imageFuture = waveform.renderViewport(context);
            imageFuture.whenComplete(
                    (image, _) -> {
                        if (image != null) {}
                    });
        } catch (Exception e) {
            // Error rendering
            e.printStackTrace();
            clearBackground(g, bounds);
        }

        // Draw fixed center playhead (always at 50% of viewport width)
        if (state == AudioSessionStateMachine.State.READY
                || state == AudioSessionStateMachine.State.PLAYING
                || state == AudioSessionStateMachine.State.PAUSED) {
            paintPlayhead(g, bounds);
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
            @NonNull Graphics2D g, @NonNull ScreenDimension bounds, @NonNull Image waveformImage) {
        // Check if we need to offset the waveform for negative viewport start (when playback is at
        // 0)
        if (dataSource != null && dataSource instanceof state.WaveformPaintDataSource) {
            state.WaveformPaintDataSource paintDataSource =
                    (state.WaveformPaintDataSource) dataSource;

            // Get playback position - if it's near 0, we need to offset the waveform
            double playbackPos = paintDataSource.getPlaybackPositionSeconds();
            double halfViewportSeconds =
                    bounds.width() / (2.0 * paintDataSource.getPixelsPerSecond());

            if (playbackPos < halfViewportSeconds) {
                // We're at the beginning - offset waveform so playhead appears centered
                int offsetPixels =
                        (int)
                                ((halfViewportSeconds - playbackPos)
                                        * paintDataSource.getPixelsPerSecond());
                g.drawImage(
                        waveformImage,
                        bounds.x() + offsetPixels,
                        bounds.y(),
                        bounds.width(),
                        bounds.height(),
                        null);
                return;
            }
        }

        // Normal drawing
        g.drawImage(waveformImage, bounds.x(), bounds.y(), bounds.width(), bounds.height(), null);
    }

    /**
     * Paint loading indicator while audio is being loaded.
     *
     * @param g Graphics context
     * @param bounds Area to paint in
     */
    public void paintLoadingIndicator(@NonNull Graphics2D g, @NonNull ScreenDimension bounds) {
        g.setColor(Color.GRAY);
        String message = "Loading...";
        int textWidth = g.getFontMetrics().stringWidth(message);
        int textHeight = g.getFontMetrics().getHeight();
        int x = bounds.x() + (bounds.width() - textWidth) / 2;
        int y = bounds.y() + (bounds.height() + textHeight) / 2;
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
            @NonNull Graphics2D g, @NonNull ScreenDimension bounds, @NonNull String errorMessage) {
        g.setColor(Color.RED);
        int textHeight = g.getFontMetrics().getHeight();
        int x = bounds.x() + 10;
        int y = bounds.y() + (bounds.height() + textHeight) / 2;
        g.drawString("Error: " + errorMessage, x, y);
    }

    /**
     * Paint empty state when no audio is loaded.
     *
     * @param g Graphics context
     * @param bounds Area to paint in
     */
    public void paintEmptyState(@NonNull Graphics2D g, @NonNull ScreenDimension bounds) {
        g.setColor(Color.GRAY);
        String message = "No audio loaded";
        int textWidth = g.getFontMetrics().stringWidth(message);
        int textHeight = g.getFontMetrics().getHeight();
        int x = bounds.x() + (bounds.width() - textWidth) / 2;
        int y = bounds.y() + (bounds.height() + textHeight) / 2;
        g.drawString(message, x, y);
    }

    /**
     * Clear the background before painting.
     *
     * @param g Graphics context
     * @param bounds Area to clear
     */
    public void clearBackground(@NonNull Graphics2D g, @NonNull ScreenDimension bounds) {
        g.setColor(Color.WHITE);
        g.fillRect(bounds.x(), bounds.y(), bounds.width(), bounds.height());
    }

    /**
     * Paint the playhead at the center of the viewport.
     *
     * @param g Graphics context
     * @param bounds Area to paint in
     */
    public void paintPlayhead(@NonNull Graphics2D g, @NonNull ScreenDimension bounds) {
        // Playhead is always at exactly 50% of viewport width
        int playheadX = bounds.x() + bounds.width() / 2;

        // Draw a vertical black line with XOR mode for visibility
        g.setXORMode(Color.WHITE);
        g.setColor(Color.BLACK);
        g.drawLine(playheadX, bounds.y(), playheadX, bounds.y() + bounds.height());
        g.setPaintMode(); // Reset to normal paint mode
    }
}
