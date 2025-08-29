package waveform;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.text.DecimalFormat;

/** Headless Graphics2D waveform rendering for display visualization. */
final class WaveformRenderer {

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

    private final WaveformScaler waveformScaler;

    public WaveformRenderer(WaveformScaler waveformScaler) {
        this.waveformScaler = waveformScaler;
    }

    /** Renders a waveform chunk as a BufferedImage using pure Java2D. */
    public Image renderWaveformChunk(
            double[] valsToDraw,
            int width,
            int height,
            double yScale,
            double startTimeSeconds,
            double biggestConsecutivePixelVals,
            int pixelsPerSecond) {

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        try {
            g2d.setRenderingHints(RENDERING_HINTS);

            g2d.setColor(WAVEFORM_BACKGROUND);
            g2d.fillRect(0, 0, width, height);

            g2d.setColor(WAVEFORM_REFERENCE_LINE);
            g2d.drawLine(0, height / 2, width, height / 2);

            drawTimeScale(g2d, width, height, startTimeSeconds, pixelsPerSecond);
            drawWaveform(g2d, valsToDraw, height, yScale);

            return image;

        } finally {
            g2d.dispose();
        }
    }

    /** Calculates the Y-axis scaling factor for waveform amplitude display. */
    public double calculateYScale(
            double[] valsToDraw, int height, double biggestConsecutivePixelVals) {
        return waveformScaler.getPixelScale(valsToDraw, height, biggestConsecutivePixelVals);
    }

    private void drawTimeScale(
            Graphics2D g2d, int width, int height, double startTimeSeconds, int pixelsPerSecond) {
        if (pixelsPerSecond <= 0) {
            return;
        }

        for (int i = 0; i < width; i += pixelsPerSecond) {
            g2d.setColor(WAVEFORM_SCALE_LINE);
            g2d.drawLine(i, 0, i, height - 1);

            g2d.setColor(WAVEFORM_SCALE_TEXT);
            double seconds = startTimeSeconds + (i / (double) pixelsPerSecond);
            g2d.drawString(SEC_FORMAT.format(seconds), i + 5, height - 5);
        }
    }

    private void drawWaveform(Graphics2D g2d, double[] valsToDraw, int height, double yScale) {
        g2d.setColor(FIRST_CHANNEL_WAVEFORM);

        final int refLinePos = height / 2;

        for (int i = 0; i < valsToDraw.length; i++) {
            double scaledSample = valsToDraw[i] * yScale;

            int topY = (int) (refLinePos - scaledSample);
            int bottomY = (int) (refLinePos + scaledSample);

            g2d.drawLine(i, refLinePos, i, topY);
            g2d.drawLine(i, refLinePos, i, bottomY);
        }
    }
}
