package waveform;

import audio.FmodCore;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import marytts.signalproc.filter.BandPassFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Audio-to-display processing pipeline for waveform visualization. */
final class WaveformProcessor {
    private static final Logger logger = LoggerFactory.getLogger(WaveformProcessor.class);

    private final FmodCore fmodCore;
    private final SignalEnhancer signalEnhancer = new SignalEnhancer();
    private final PixelScaler pixelScaler;
    private final WaveformScaler waveformScaler;
    private final ConcurrentHashMap<FrequencyRange, BandPassFilter> filterCache;

    public WaveformProcessor(
            FmodCore fmodCore,
            PixelScaler pixelScaler,
            WaveformScaler waveformScaler,
            boolean cachingEnabled) {
        this.fmodCore = fmodCore;
        this.pixelScaler = pixelScaler;
        this.waveformScaler = waveformScaler;
        this.filterCache = cachingEnabled ? new ConcurrentHashMap<>() : null;
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
            // During file switches or rapid state changes, FMOD may transiently fail reads.
            // Avoid a noisy stack trace; log a concise warning and render an empty strip.
            logger.warn("Failed to read audio chunk {} using FMOD: {}", chunkIndex, e.getMessage());
            return new double[targetPixelWidth];
        }
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
                chunkData.samples, // FMOD already returns new array, no clone needed
                chunkData.sampleRate,
                0.0, // Peak calculated after processing
                chunkData.totalFrames,
                chunkData.overlapFrames);
    }

    /** Applies signal processing to raw audio data. */
    private AudioChunkData processSignal(AudioChunkData rawAudio, FrequencyRange frequencyFilter) {
        // BandPassFilter.apply() returns a new array, so no defensive copy needed
        double[] samples = rawAudio.amplitudeValues();

        if (samples.length > 0) {
            BandPassFilter filter;
            if (filterCache != null) {
                filter =
                        filterCache.computeIfAbsent(
                                frequencyFilter,
                                range ->
                                        new BandPassFilter(
                                                range.minFrequency(), range.maxFrequency()));
            } else {
                filter =
                        new BandPassFilter(
                                frequencyFilter.minFrequency(), frequencyFilter.maxFrequency());
            }
            samples = filter.apply(samples); // This returns new array
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
