package waveform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Converts audio samples to pixel resolution and applies smoothing. */
public final class PixelScaler {
    private static final Logger logger = LoggerFactory.getLogger(PixelScaler.class);

    /** Converts audio samples to pixel resolution for display. */
    public double[] toPixelResolution(
            double[] samples,
            int skipInitialSamples,
            int targetPixelWidth,
            int numSamplesAvailable) {
        if (targetPixelWidth <= 0) {
            throw new IllegalArgumentException(
                    "Target pixel width must be > 0: " + targetPixelWidth);
        }
        if (skipInitialSamples < 0 || skipInitialSamples >= samples.length) {
            throw new IllegalArgumentException("Invalid skip count: " + skipInitialSamples);
        }

        int availableSamples = samples.length - skipInitialSamples;
        if (availableSamples <= 0) {
            return new double[targetPixelWidth];
        }

        double[] pixelValues = new double[targetPixelWidth];
        final double sampleIncrement = (double) availableSamples / targetPixelWidth;

        for (int i = 0; i < pixelValues.length; i++) {
            int index = (int) (i * sampleIncrement) + skipInitialSamples;
            if (index > numSamplesAvailable - 1) {
                break;
            }
            pixelValues[i] = samples[index];
        }

        logger.debug(
                "Converted {} samples to {} pixels (increment={})",
                samples.length,
                targetPixelWidth,
                sampleIncrement);
        return pixelValues;
    }

    /** Applies visual smoothing to remove display artifacts. */
    public double[] smoothPixels(double[] pixelValues) {
        if (pixelValues.length < 3) {
            return pixelValues;
        }

        double[] copy = new double[pixelValues.length];
        System.arraycopy(pixelValues, 0, copy, 0, pixelValues.length);

        // Smooth peaks
        for (int i = 1; i < copy.length - 1; i++) {
            if (copy[i] > copy[i - 1] && copy[i] > copy[i + 1]) {
                pixelValues[i] = Math.max(copy[i + 1], copy[i - 1]);
            }
        }

        // Smooth valleys
        for (int i = 1; i < copy.length - 1; i++) {
            if (copy[i] < copy[i - 1] && copy[i] < copy[i + 1]) {
                pixelValues[i] = Math.min(copy[i + 1], copy[i - 1]);
            }
        }

        logger.debug("Applied pixel smoothing to {} pixels", pixelValues.length);
        return pixelValues;
    }

    /** Calculates peak amplitude for rendering decisions. */
    public double getRenderingPeak(double[] pixelValues, int skipInitialPixels) {
        if (pixelValues.length < skipInitialPixels + 2) {
            return 0;
        }

        double maxConsecutive = 0;
        for (int i = skipInitialPixels; i < pixelValues.length - 1; i++) {
            double consecutiveVals = Math.min(pixelValues[i], pixelValues[i + 1]);
            maxConsecutive = Math.max(consecutiveVals, maxConsecutive);
        }

        return maxConsecutive;
    }
}
