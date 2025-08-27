package waveform;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.text.DecimalFormat;
import ui.UiColors;
import ui.UiConstants;
import ui.UiShapes;

/**
 * Pure Graphics2D-based waveform rendering without Swing dependencies.
 *
 * <p>This class is headless-compatible and handles all visual aspects of waveform display:
 * background, reference lines, time scales, and waveform drawing.
 */
public final class WaveformRenderer {

    private final DecimalFormat secFormat = new DecimalFormat("0.00s");
    private final WaveformScaler waveformScaler;

    public WaveformRenderer(WaveformScaler waveformScaler) {
        this.waveformScaler = waveformScaler;
    }

    /**
     * Renders a waveform chunk as a BufferedImage using pure Java2D.
     *
     * @param valsToDraw pre-processed waveform sample values for display
     * @param width image width in pixels
     * @param height image height in pixels
     * @param yScale vertical scaling factor for amplitude display
     * @param startTimeSeconds starting time for this chunk (for time scale labels)
     * @param biggestConsecutivePixelVals peak value for scaling reference
     * @return rendered waveform image (headless-compatible)
     */
    public Image renderWaveformChunk(
            double[] valsToDraw,
            int width,
            int height,
            double yScale,
            double startTimeSeconds,
            double biggestConsecutivePixelVals) {

        // Create headless-compatible image
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        try {
            // Apply rendering hints for quality
            g2d.setRenderingHints(UiShapes.getRenderingHints());

            // Fill background
            g2d.setColor(UiColors.waveformBackground);
            g2d.fillRect(0, 0, width, height);

            // Draw reference line (center horizontal line)
            g2d.setColor(UiColors.waveformReferenceLineColor);
            g2d.drawLine(0, height / 2, width, height / 2);

            // Draw time scale lines and labels
            drawTimeScale(g2d, width, height, startTimeSeconds);

            // Draw the actual waveform
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

    private void drawTimeScale(Graphics2D g2d, int width, int height, double startTimeSeconds) {
        double counter = startTimeSeconds;

        for (int i = 0; i < width; i += UiConstants.zoomlessPixelsPerSecond) {
            // Draw vertical scale line
            g2d.setColor(UiColors.waveformScaleLineColor);
            g2d.drawLine(i, 0, i, height - 1);

            // Draw time label
            g2d.setColor(UiColors.waveformScaleTextColor);
            g2d.drawString(secFormat.format(counter), i + 5, height - 5);
            counter++;
        }
    }

    private void drawWaveform(Graphics2D g2d, double[] valsToDraw, int height, double yScale) {
        g2d.setColor(UiColors.firstChannelWaveformColor);

        final int refLinePos = height / 2;

        for (int i = 0; i < valsToDraw.length; i++) {
            // Apply yScale to sample value
            double scaledSample = valsToDraw[i] * yScale;

            // Calculate positions above and below reference line
            int topY = (int) (refLinePos - scaledSample);
            int bottomY = (int) (refLinePos + scaledSample);

            // Draw waveform lines from center to peaks
            g2d.drawLine(i, refLinePos, i, topY);
            g2d.drawLine(i, refLinePos, i, bottomY);
        }
    }
}
