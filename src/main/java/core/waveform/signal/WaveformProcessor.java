package core.waveform.signal;

import core.audio.AudioData;
import core.audio.SampleReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;
import marytts.signalproc.filter.BandPassFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Audio-to-display processing pipeline for waveform visualization. */
public final class WaveformProcessor {
    private static final Logger logger = LoggerFactory.getLogger(WaveformProcessor.class);

    public static final double STANDARD_CHUNK_DURATION_SECONDS =
            10.0; // Standard chunk size for processing

    // Signal processing constants
    private static final double MIN_FREQUENCY = 0.001; // 0.1% of Nyquist frequency
    private static final double MAX_FREQUENCY = 0.45; // 45% of Nyquist frequency
    private static final double PRE_DATA_OVERLAP_SECONDS = 0.25; // Overlap for chunk boundaries

    private final SampleReader sampleReader;
    private final int sampleRate;
    private final SignalEnhancer signalEnhancer = new SignalEnhancer();
    private final PixelScaler pixelScaler;
    private final ConcurrentHashMap<FrequencyRange, BandPassFilter> filterCache;

    public WaveformProcessor(SampleReader sampleReader, int sampleRate, PixelScaler pixelScaler) {
        this.sampleReader = sampleReader;
        this.sampleRate = sampleRate;
        this.pixelScaler = pixelScaler;
        this.filterCache = new ConcurrentHashMap<>();
    }

    /** Processes audio and scales for display in one call. */
    public double[] processAudioForDisplay(
            String audioFilePath, int chunkIndex, int targetPixelWidth) {

        try {
            AudioChunkData rawAudio =
                    loadChunk(
                            audioFilePath,
                            chunkIndex,
                            STANDARD_CHUNK_DURATION_SECONDS,
                            PRE_DATA_OVERLAP_SECONDS);

            FrequencyRange frequencyFilter = new FrequencyRange(MIN_FREQUENCY, MAX_FREQUENCY);
            AudioChunkData processedAudio = processSignal(rawAudio, frequencyFilter);

            return scaleToDisplay(processedAudio, targetPixelWidth);

        } catch (IOException e) {
            // During file switches or rapid state changes, FMOD may transiently fail reads.
            // Avoid a noisy stack trace; log a concise warning and render an empty strip.
            logger.warn("Failed to read audio chunk {} using FMOD: {}", chunkIndex, e.getMessage());
            return new double[targetPixelWidth];
        }
    }

    /** Loads raw audio chunk from file using SampleReader. */
    private AudioChunkData loadChunk(
            String audioFilePath,
            int chunkIndex,
            double chunkDurationSeconds,
            double overlapSeconds)
            throws IOException {

        try {
            // Calculate frame positions
            long startFrame = (long) (chunkIndex * chunkDurationSeconds * sampleRate);
            long frameCount = (long) (chunkDurationSeconds * sampleRate);
            long overlapFrames = (long) (overlapSeconds * sampleRate);

            // Only use overlap for chunks after the first one
            int actualOverlapFrames = 0;

            // Adjust for overlap
            if (chunkIndex > 0) {
                startFrame -= overlapFrames;
                frameCount += overlapFrames;
                actualOverlapFrames = (int) overlapFrames;
            }

            // Read samples asynchronously but wait for result
            Path audioPath = Paths.get(audioFilePath);
            AudioData audioData = sampleReader.readSamples(audioPath, startFrame, frameCount).get();

            // If the read returned no frames (e.g., beyond EOF), disable overlap to avoid
            // invalid skip counts during pixel scaling.
            int safeOverlapFrames = (audioData.frameCount() <= 0) ? 0 : actualOverlapFrames;

            return new AudioChunkData(
                    audioData.samples(),
                    audioData.sampleRate(),
                    0.0, // Peak calculated after processing
                    (int) audioData.frameCount(),
                    safeOverlapFrames);
        } catch (Exception e) {
            throw new IOException("Failed to read audio chunk: " + e.getMessage(), e);
        }
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
