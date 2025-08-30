package waveform;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;

/** Headless Graphics2D waveform rendering for display visualization. */
final class WaveformRenderer {

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
            WaveformDrawingUtils.setupRenderingHints(g2d);
            WaveformDrawingUtils.drawBackground(g2d, width, height);
            WaveformDrawingUtils.drawReferenceLine(g2d, width, height);
            WaveformDrawingUtils.drawTimeScale(
                    g2d, width, height, startTimeSeconds, pixelsPerSecond);
            WaveformDrawingUtils.drawWaveform(g2d, valsToDraw, 0, width, height, yScale);

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
}
