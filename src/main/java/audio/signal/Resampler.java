package audio.signal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Standard DSP resampling operations for converting between sample rates and resolutions.
 *
 * <p>This class implements the core resampling algorithms used throughout audio processing,
 * following industry-standard DSP terminology and practices.
 */
public class Resampler {
    private static final Logger logger = LoggerFactory.getLogger(Resampler.class);

    /**
     * Downsamples audio data to pixel resolution using the exact algorithm from WaveformBuffer.
     *
     * <p>This implements the "extract some of the samples for representation as pixels"
     * logic from WaveformBuffer.getValsToDraw() lines 390-400.
     *
     * @param samples source sample data (high resolution, post-filtered)
     * @param skipInitialSamples number of samples to skip (preDataSizeInFrames)
     * @param targetLength desired output length (chunkWidthInPixels)
     * @param numSamplesAvailable actual number of valid samples available (numSamplesLeft)
     * @return new array with exactly targetLength samples
     */
    public double[] downsample(double[] samples, int skipInitialSamples, int targetLength, int numSamplesAvailable) {
        if (targetLength <= 0) {
            throw new IllegalArgumentException("Target length must be > 0: " + targetLength);
        }
        if (skipInitialSamples < 0 || skipInitialSamples >= samples.length) {
            throw new IllegalArgumentException("Invalid skip count: " + skipInitialSamples);
        }
        
        int availableSamples = samples.length - skipInitialSamples;
        if (availableSamples <= 0) {
            return new double[targetLength];
        }
        
        // extract some of the samples for representation as pixels
        double[] valsToDraw = new double[targetLength];
        final double sampleIncrement = (double) availableSamples / targetLength;
        
        for (int i = 0; i < valsToDraw.length; i++) {
            int index = (int) (i * sampleIncrement) + skipInitialSamples;
            if (index > numSamplesAvailable - 1) {
                break;
            }
            valsToDraw[i] = samples[index];
        }
        
        logger.debug("Downsampled {} samples to {} pixels (increment={})", 
                     samples.length, targetLength, sampleIncrement);
        return valsToDraw;
    }

    /**
     * Upsamples audio data using linear interpolation.
     */
    public double[] upsample(double[] samples, int targetLength) {
        if (targetLength <= 0) {
            throw new IllegalArgumentException("Target length must be > 0: " + targetLength);
        }
        if (samples.length == 0) {
            return new double[targetLength];
        }
        
        double[] result = new double[targetLength];
        
        if (samples.length == 1) {
            java.util.Arrays.fill(result, samples[0]);
            return result;
        }
        
        double scale = (double) (samples.length - 1) / (targetLength - 1);
        
        for (int i = 0; i < targetLength; i++) {
            double sourceIndex = i * scale;
            int lowerIndex = (int) Math.floor(sourceIndex);
            int upperIndex = Math.min(lowerIndex + 1, samples.length - 1);
            
            if (lowerIndex == upperIndex) {
                result[i] = samples[lowerIndex];
            } else {
                double fraction = sourceIndex - lowerIndex;
                result[i] = samples[lowerIndex] * (1 - fraction) + samples[upperIndex] * fraction;
            }
        }
        
        logger.debug("Upsampled {} samples to {}", samples.length, targetLength);
        return result;
    }

    /**
     * Applies pixel-level smoothing to remove visual artifacts from downsampled data.
     *
     * <p>This implements the "make the waveform prettier by smoothing the pixels"
     * algorithm from WaveformBuffer.getValsToDraw() lines 402-421. It removes
     * isolated peaks and troughs that might be visually distracting.
     *
     * @param samples pixel-resolution sample data (modified in-place)
     * @return the same samples array for method chaining
     */
    public double[] smoothPixels(double[] samples) {
        if (samples.length < 3) {
            return samples; // Need at least 3 points for smoothing
        }
        
        // make the waveform prettier by smoothing the pixels
        for (int j = 0; j < 1; j++) {
            double[] copy2 = new double[samples.length];
            System.arraycopy(samples, 0, copy2, 0, samples.length);
            for (int i = 1; i < copy2.length - 1; i++) {
                if (copy2[i] > copy2[i - 1]) {
                    if (copy2[i] > copy2[i + 1]) {
                        samples[i] = Math.max(copy2[i + 1], copy2[i - 1]);
                    }
                }
            }
            for (int i = 1; i < copy2.length - 1; i++) {
                if (copy2[i] < copy2[i - 1]) {
                    if (copy2[i] < copy2[i + 1]) {
                        samples[i] = Math.min(copy2[i + 1], copy2[i - 1]);
                    }
                }
            }
        }
        
        logger.debug("Applied pixel smoothing to {} samples", samples.length);
        return samples;
    }

    /**
     * Calculates the decimation ratio for downsampling operations.
     */
    public double getDecimationRatio(int sourceLength, int targetLength) {
        if (targetLength <= 0) {
            throw new IllegalArgumentException("Target length must be > 0: " + targetLength);
        }
        return (double) sourceLength / targetLength;
    }
}