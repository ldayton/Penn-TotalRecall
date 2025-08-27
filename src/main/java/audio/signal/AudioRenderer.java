package audio.signal;

import com.google.inject.Singleton;
import marytts.signalproc.filter.BandPassFilter;
import marytts.util.data.audio.AudioDoubleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Audio-to-visual rendering operations for waveform display.
 *
 * <p>This class handles the conversion of raw audio data into displayable waveform representations,
 * including filtering and envelope detection.
 */
@Singleton
public class AudioRenderer {
    private static final Logger logger = LoggerFactory.getLogger(AudioRenderer.class);

    /**
     * Applies bandpass filtering to audio samples using MaryTTS filter implementation.
     *
     * @param audioSource the audio data source containing raw PCM samples
     * @param minBand normalized minimum frequency (0.0 to 0.5, where 0.5 = Nyquist)
     * @param maxBand normalized maximum frequency (0.0 to 0.5, where 0.5 = Nyquist)
     * @param outputSamples pre-allocated array to receive filtered samples
     * @return the same outputSamples array for method chaining
     */
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

    /**
     * Applies envelope smoothing to reduce visual noise in waveform display.
     *
     * <p>For each sample position, finds the maximum absolute amplitude within a symmetric window
     * around that position. This creates visually appealing waveforms by emphasizing sustained loud
     * sections while reducing isolated spikes.
     *
     * <p>This is the actual algorithm from WaveformBuffer.getValsToDraw() that makes "waveform
     * prettier by smoothing the audio data (~60ms)".
     *
     * @param samples input sample data (modified in-place)
     * @param windowSize size of sliding window in samples (WaveformBuffer uses 20)
     * @return the same samples array for method chaining
     */
    public double[] envelopeSmooth(double[] samples, int windowSize) {
        if (windowSize < 1) {
            throw new IllegalArgumentException("Window size must be >= 1: " + windowSize);
        }

        // Copy original data to avoid modifying source during window calculations
        double[] copy = new double[samples.length];
        System.arraycopy(samples, 0, copy, 0, copy.length);

        // make the waveform prettier by smoothing the audio data (~60ms)
        double biggestInWindow;
        int start;
        int end;

        for (int i = 0; i < samples.length; i++) {
            biggestInWindow = 0;
            start = Math.max(0, i - windowSize);
            end = Math.min(samples.length, i + windowSize);
            for (int j = start; j < end; j++) {
                biggestInWindow = Math.max(biggestInWindow, Math.abs(copy[j]));
            }
            samples[i] = biggestInWindow;
        }

        logger.debug(
                "Applied envelope smoothing (window={}) to {} samples", windowSize, samples.length);
        return samples;
    }

    /**
     * Finds peak sustained amplitude for rendering decisions.
     *
     * <p>Looks for maximum amplitude that occurs in adjacent samples, representing sustained audio
     * content versus brief spikes.
     *
     * <p>This is the "biggestConsecutivePixelVals" algorithm from WaveformBuffer that finds
     * "largest value that 2 consecutive pixels will actually draw at".
     *
     * @param samples sample data to analyze (typically pixel-resolution data)
     * @param skipInitialSamples number of initial samples to skip (WaveformBuffer uses
     *     pixelsPerSecond/2)
     * @return maximum sustained amplitude found
     */
    public double getRenderingPeak(double[] samples, int skipInitialSamples) {
        if (samples.length < skipInitialSamples + 2) {
            return 0;
        }

        double biggestConsecutivePixelVals = 0;

        for (int i = skipInitialSamples; i < samples.length - 1; i++) {
            double consecutiveVals = Math.min(samples[i], samples[i + 1]);
            biggestConsecutivePixelVals = Math.max(consecutiveVals, biggestConsecutivePixelVals);
        }

        return biggestConsecutivePixelVals;
    }
}
