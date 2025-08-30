package waveform;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.text.DecimalFormat;

/** Shared waveform drawing utilities for consistent rendering across implementations. */
public final class WaveformDrawingUtils {

    private static final DecimalFormat SEC_FORMAT = new DecimalFormat("0.00s");

    // Waveform rendering constants (colors, hints)
    public static final Color WAVEFORM_BACKGROUND = Color.WHITE;
    public static final Color WAVEFORM_REFERENCE_LINE = Color.BLACK;
    public static final Color WAVEFORM_SCALE_LINE = new Color(226, 224, 131);
    public static final Color WAVEFORM_SCALE_TEXT = Color.BLACK;
    public static final Color FIRST_CHANNEL_WAVEFORM = Color.BLACK;
    public static final RenderingHints RENDERING_HINTS =
            new RenderingHints(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    static {
        RENDERING_HINTS.put(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        RENDERING_HINTS.put(
                RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        RENDERING_HINTS.put(
                RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }

    /** Draw background fill. */
    public static void drawBackground(Graphics2D g, int width, int height) {
        g.setColor(WAVEFORM_BACKGROUND);
        g.fillRect(0, 0, width, height);
    }

    /** Draw center reference line. */
    public static void drawReferenceLine(Graphics2D g, int width, int height) {
        g.setColor(WAVEFORM_REFERENCE_LINE);
        g.drawLine(0, height / 2, width, height / 2);
    }

    /** Draw time scale grid and labels. */
    public static void drawTimeScale(
            Graphics2D g, int width, int height, double startTimeSeconds, int pixelsPerSecond) {
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

    /**
     * Draw waveform data.
     *
     * @param g Graphics context
     * @param valsToDraw Audio sample values to draw
     * @param startX X position to start drawing
     * @param width Number of pixels to draw (may be less than valsToDraw.length)
     * @param height Image height
     * @param yScale Amplitude scaling factor
     */
    public static void drawWaveform(
            Graphics2D g, double[] valsToDraw, int startX, int width, int height, double yScale) {
        g.setColor(FIRST_CHANNEL_WAVEFORM);

        final int centerY = height / 2;
        final int pixelsToDraw = Math.min(width, valsToDraw.length);

        for (int i = 0; i < pixelsToDraw; i++) {
            double scaledSample = valsToDraw[i] * yScale;
            int topY = (int) (centerY - scaledSample);
            int bottomY = (int) (centerY + scaledSample);

            int x = startX + i;
            g.drawLine(x, centerY, x, topY);
            g.drawLine(x, centerY, x, bottomY);
        }
    }

    /** Setup rendering hints for quality output. */
    public static void setupRenderingHints(Graphics2D g) {
        g.setRenderingHints(RENDERING_HINTS);
    }
}
