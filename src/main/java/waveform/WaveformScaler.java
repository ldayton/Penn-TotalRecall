package waveform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Scaling utilities for waveform amplitude visualization. */
final class WaveformScaler {
    private static final Logger logger = LoggerFactory.getLogger(WaveformScaler.class);

    /** Calculates optimal pixel scaling factor for waveform display. */
    public double getPixelScale(
            double[] samples, int displayHeight, double biggestConsecutivePixelVals) {
        if (displayHeight <= 0) {
            throw new IllegalArgumentException("Display height must be > 0: " + displayHeight);
        }

        if (biggestConsecutivePixelVals <= 0) {
            logger.warn("No meaningful amplitude found, using default scale");
            return 0; // WaveformBuffer uses 0, not 1.0!
        }

        double yScale = ((displayHeight / 2) - 1) / biggestConsecutivePixelVals;

        if (Double.isInfinite(yScale) || Double.isNaN(yScale)) {
            logger.warn("yScale is infinite in magnitude, or not a number, using 0 instead");
            yScale = 0;
        }

        // logger.debug(
        //         "Calculated pixel scale: {} (peak={}, height={})",
        //         yScale,
        //         biggestConsecutivePixelVals,
        //         displayHeight);
        return yScale;
    }
}
