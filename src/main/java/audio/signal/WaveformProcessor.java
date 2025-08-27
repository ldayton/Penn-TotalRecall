package audio.signal;

import audio.FmodCore;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import marytts.signalproc.filter.BandPassFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Complete audio-to-waveform processing pipeline.
 *
 * <p>Orchestrates the entire workflow from raw audio file data to display-ready waveform amplitude
 * values, including FMOD loading, frequency filtering, envelope smoothing, and resampling to target
 * resolution.
 */
@Singleton
public class WaveformProcessor {
    private static final Logger logger = LoggerFactory.getLogger(WaveformProcessor.class);

    private final FmodCore fmodCore;
    private final AudioRenderer audioRenderer;
    private final Resampler resampler;

    @Inject
    public WaveformProcessor(FmodCore fmodCore, AudioRenderer audioRenderer, Resampler resampler) {
        this.fmodCore = fmodCore;
        this.audioRenderer = audioRenderer;
        this.resampler = resampler;
    }

    /**
     * Processes raw audio data into display-ready waveform amplitude values.
     *
     * @param audioFilePath absolute path to the audio file
     * @param chunkIndex zero-based chunk number to process
     * @param chunkSizeSeconds duration of each chunk in seconds
     * @param overlapSeconds seconds of overlap for signal processing context
     * @param minFreqBand minimum frequency for bandpass filtering (normalized)
     * @param maxFreqBand maximum frequency for bandpass filtering (normalized)
     * @param targetPixelWidth target number of amplitude values for display
     * @return array of amplitude values ready for waveform display, or empty array on failure
     */
    public double[] processAudioForDisplay(
            String audioFilePath,
            int chunkIndex,
            double chunkSizeSeconds,
            double overlapSeconds,
            double minFreqBand,
            double maxFreqBand,
            int targetPixelWidth) {

        try {
            // Step 1: Load raw audio chunk using FMOD
            FmodCore.ChunkData chunkData =
                    fmodCore.readAudioChunk(
                            audioFilePath, chunkIndex, chunkSizeSeconds, overlapSeconds);

            double[] samples = chunkData.samples.clone(); // Clone to avoid modifying original
            int preDataSizeInFrames = chunkData.overlapFrames;

            // Step 2: Apply bandpass frequency filtering
            if (samples.length > 0) {
                BandPassFilter filter = new BandPassFilter(minFreqBand, maxFreqBand);
                samples = filter.apply(samples);
            }

            // Step 3: Apply envelope smoothing to reduce visual noise
            audioRenderer.envelopeSmooth(samples, 20);

            // Step 4: Downsample to target pixel resolution
            double[] valsToDraw =
                    resampler.downsample(
                            samples, preDataSizeInFrames, targetPixelWidth, chunkData.totalFrames);

            // Step 5: Final pixel-level smoothing for clean visual rendering
            resampler.smoothPixels(valsToDraw);

            return valsToDraw;

        } catch (IOException e) {
            logger.error("Failed to read audio chunk " + chunkIndex + " using FMOD", e);
            // Return empty array as fallback
            return new double[targetPixelWidth];
        }
    }
}
