package waveform;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.text.DecimalFormat;
import ui.UiColors;
import ui.UiConstants;
import ui.UiShapes;

/** Headless Graphics2D waveform rendering for display visualization. */
public final class WaveformRenderer {

    private final DecimalFormat secFormat = new DecimalFormat("0.00s");
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
            double biggestConsecutivePixelVals) {

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        try {
            g2d.setRenderingHints(UiShapes.getRenderingHints());

            g2d.setColor(UiColors.waveformBackground);
            g2d.fillRect(0, 0, width, height);

            g2d.setColor(UiColors.waveformReferenceLineColor);
            g2d.drawLine(0, height / 2, width, height / 2);

            drawTimeScale(g2d, width, height, startTimeSeconds);
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
            g2d.setColor(UiColors.waveformScaleLineColor);
            g2d.drawLine(i, 0, i, height - 1);

            g2d.setColor(UiColors.waveformScaleTextColor);
            g2d.drawString(secFormat.format(counter), i + 5, height - 5);
            counter++;
        }
    }

    private void drawWaveform(Graphics2D g2d, double[] valsToDraw, int height, double yScale) {
        g2d.setColor(UiColors.firstChannelWaveformColor);

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