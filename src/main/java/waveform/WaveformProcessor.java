package waveform;

import audio.FmodCore;
import java.io.IOException;
import marytts.signalproc.filter.BandPassFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Audio-to-display processing pipeline for waveform visualization. */
public final class WaveformProcessor {
    private static final Logger logger = LoggerFactory.getLogger(WaveformProcessor.class);

    private final FmodCore fmodCore;
    private final SignalEnhancer signalEnhancer = new SignalEnhancer();
    private final PixelScaler pixelScaler;
    private final WaveformScaler waveformScaler;

    public WaveformProcessor(
            FmodCore fmodCore, PixelScaler pixelScaler, WaveformScaler waveformScaler) {
        this.fmodCore = fmodCore;
        this.pixelScaler = pixelScaler;
        this.waveformScaler = waveformScaler;
    }

    /** Processes audio and scales for display in one call. */
    public double[] processAudioForDisplay(
            String audioFilePath,
            int chunkIndex,
            double chunkDurationSeconds,
            double overlapSeconds,
            double minFrequency,
            double maxFrequency,
            int targetPixelWidth) {

        try {
            AudioChunkData rawAudio =
                    loadChunk(audioFilePath, chunkIndex, chunkDurationSeconds, overlapSeconds);

            FrequencyRange frequencyFilter = new FrequencyRange(minFrequency, maxFrequency);
            AudioChunkData processedAudio = processSignal(rawAudio, frequencyFilter);

            return scaleToDisplay(processedAudio, targetPixelWidth);

        } catch (IOException e) {
            logger.error("Failed to read audio chunk " + chunkIndex + " using FMOD", e);
            return new double[targetPixelWidth];
        }
    }

    /** Calculates Y-axis scaling factor for display. */
    public double calculateYScale(double[] amplitudes, int displayHeight, double peakReference) {
        return waveformScaler.getPixelScale(amplitudes, displayHeight, peakReference);
    }

    /** Loads raw audio chunk from file using FMOD. */
    private AudioChunkData loadChunk(
            String audioFilePath,
            int chunkIndex,
            double chunkDurationSeconds,
            double overlapSeconds)
            throws IOException {

        FmodCore.ChunkData chunkData =
                fmodCore.readAudioChunk(
                        audioFilePath, chunkIndex, chunkDurationSeconds, overlapSeconds);

        return new AudioChunkData(
                chunkData.samples.clone(),
                chunkData.sampleRate,
                0.0, // Peak calculated after processing
                chunkData.totalFrames,
                chunkData.overlapFrames);
    }

    /** Applies signal processing to raw audio data. */
    private AudioChunkData processSignal(AudioChunkData rawAudio, FrequencyRange frequencyFilter) {
        double[] samples = rawAudio.amplitudeValues().clone();

        if (samples.length > 0) {
            BandPassFilter filter =
                    new BandPassFilter(frequencyFilter.minFrequency(), frequencyFilter.maxFrequency());
            samples = filter.apply(samples);
        }

        signalEnhancer.envelopeSmooth(samples, 20);

        return new AudioChunkData(
                samples,
                rawAudio.sampleRate(),
                0.0, // Peak calculated later by WaveformBuffer if needed
                rawAudio.frameCount(),
                rawAudio.overlapFrames());
    }

    /** Scales processed audio data to display pixel resolution. */
    private double[] scaleToDisplay(AudioChunkData processedAudio, int targetPixelWidth) {
        double[] displayAmplitudes =
                pixelScaler.toPixelResolution(
                        processedAudio.amplitudeValues(),
                        processedAudio.overlapFrames(),
                        targetPixelWidth,
                        processedAudio.frameCount());

        pixelScaler.smoothPixels(displayAmplitudes);
        return displayAmplitudes;
    }
}