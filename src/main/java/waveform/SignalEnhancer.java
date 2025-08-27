package waveform;

import marytts.signalproc.filter.BandPassFilter;
import marytts.util.data.audio.AudioDoubleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Signal processing operations for audio enhancement. */
final class SignalEnhancer {
    private static final Logger logger = LoggerFactory.getLogger(SignalEnhancer.class);

    /** Applies bandpass filtering to audio samples using MaryTTS filter implementation. */
    public double[] bandpassFilter(
            AudioDoubleDataSource audioSource,
            double minBand,
            double maxBand,
            double[] outputSamples) {
        if (minBand < 0 || maxBand > 0.5 || minBand >= maxBand) {
            throw new IllegalArgumentException(
                    "Invalid bandpass range: " + minBand + " to " + maxBand);
        }

        BandPassFilter filter = new BandPassFilter(minBand, maxBand);
        filter.apply(audioSource).getData(outputSamples);

        logger.debug(
                "Applied bandpass filter {}-{} Hz to {} samples",
                minBand,
                maxBand,
                outputSamples.length);
        return outputSamples;
    }

    /** Applies envelope smoothing to reduce noise in audio signal. */
    public double[] envelopeSmooth(double[] samples, int windowSize) {
        if (windowSize < 1) {
            throw new IllegalArgumentException("Window size must be >= 1: " + windowSize);
        }

        double[] copy = new double[samples.length];
        System.arraycopy(samples, 0, copy, 0, copy.length);

        for (int i = 0; i < samples.length; i++) {
            double biggestInWindow = 0;
            int start = Math.max(0, i - windowSize);
            int end = Math.min(samples.length, i + windowSize);
            for (int j = start; j < end; j++) {
                biggestInWindow = Math.max(biggestInWindow, Math.abs(copy[j]));
            }
            samples[i] = biggestInWindow;
        }

        logger.debug(
                "Applied envelope smoothing (window={}) to {} samples", windowSize, samples.length);
        return samples;
    }

    /** Calculates peak amplitude for signal analysis. */
    public double calculatePeak(double[] samples, int skipInitialSamples) {
        if (samples.length < skipInitialSamples + 2) {
            return 0;
        }

        double maxSustained = 0;
        for (int i = skipInitialSamples; i < samples.length - 1; i++) {
            double consecutiveVals = Math.min(samples[i], samples[i + 1]);
            maxSustained = Math.max(consecutiveVals, maxSustained);
        }
        return maxSustained;
    }
}
