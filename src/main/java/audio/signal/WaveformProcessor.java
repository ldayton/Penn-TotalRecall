package audio.signal;

import audio.FmodCore;
import graphics.WaveformScaler;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import marytts.signalproc.filter.BandPassFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Primary interface for external consumers needing waveform display data.
 * 
 * Complete audio-to-display pipeline: file loading, signal processing, and display scaling.
 * Consumers only interact with this class - internal pipeline is handled automatically.
 */
@Singleton
public class WaveformProcessor {
    private static final Logger logger = LoggerFactory.getLogger(WaveformProcessor.class);
    
    private final FmodCore fmodCore;
    private final SignalEnhancer signalEnhancer;
    private final PixelScaler pixelScaler;
    private final WaveformScaler waveformScaler;
    
    @Inject
    public WaveformProcessor(
            FmodCore fmodCore,
            SignalEnhancer signalEnhancer,
            PixelScaler pixelScaler,
            WaveformScaler waveformScaler) {
        this.fmodCore = fmodCore;
        this.signalEnhancer = signalEnhancer;
        this.pixelScaler = pixelScaler;
        this.waveformScaler = waveformScaler;
    }
    
    /**
     * Primary interface: processes audio and scales for display in one call.
     * 
     * @param audioFilePath absolute path to the audio file
     * @param chunkIndex zero-based chunk number to process  
     * @param chunkDurationSeconds duration of chunk in seconds
     * @param overlapSeconds seconds of overlap for signal processing context
     * @param frequencyFilter frequency range for bandpass filtering
     * @param targetPixelWidth target number of pixels for display
     * @return amplitude values scaled to target pixel resolution, or empty array on failure
     */
    public double[] processAudioForDisplay(
            String audioFilePath,
            int chunkIndex,
            double chunkDurationSeconds,
            double overlapSeconds, 
            double minFrequency,
            double maxFrequency,
            int targetPixelWidth) {
            
        try {
            // Load raw audio data
            AudioChunkData rawAudio = loadChunk(audioFilePath, chunkIndex, chunkDurationSeconds, overlapSeconds);
            
            // Process signal (filtering, smoothing)
            FrequencyRange frequencyFilter = new FrequencyRange(minFrequency, maxFrequency);
            AudioChunkData processedAudio = processSignal(rawAudio, frequencyFilter);
            
            // Scale to display coordinates
            return scaleToDisplay(processedAudio, targetPixelWidth);
            
        } catch (IOException e) {
            logger.error("Failed to read audio chunk " + chunkIndex + " using FMOD", e);
            // Return empty array as fallback  
            return new double[targetPixelWidth];
        }
    }
    
    /**
     * Calculates Y-axis scaling factor for display.
     * 
     * @param amplitudes amplitude values to scale
     * @param displayHeight target display height in pixels
     * @param peakReference peak amplitude for scaling reference
     * @return Y-axis scaling factor
     */
    public double calculateYScale(double[] amplitudes, int displayHeight, double peakReference) {
        return waveformScaler.getPixelScale(amplitudes, displayHeight, peakReference);
    }
    
    /**
     * Loads raw audio chunk from file using FMOD.
     */
    private AudioChunkData loadChunk(
            String audioFilePath,
            int chunkIndex,
            double chunkDurationSeconds,
            double overlapSeconds) throws IOException {
            
        FmodCore.ChunkData chunkData = fmodCore.readAudioChunk(
                audioFilePath, chunkIndex, chunkDurationSeconds, overlapSeconds);
        
        return new AudioChunkData(
                chunkData.samples.clone(),
                chunkData.sampleRate,
                0.0, // Peak calculated after processing
                chunkData.totalFrames,
                chunkData.overlapFrames);
    }
    
    /**
     * Applies signal processing to raw audio data.
     */
    private AudioChunkData processSignal(AudioChunkData rawAudio, FrequencyRange frequencyFilter) {
        double[] samples = rawAudio.amplitudeValues.clone();
        
        // Apply frequency filtering if samples exist
        if (samples.length > 0) {
            BandPassFilter filter = new BandPassFilter(
                    frequencyFilter.minFrequency, 
                    frequencyFilter.maxFrequency);
            samples = filter.apply(samples);
        }
        
        // Apply signal enhancement
        signalEnhancer.envelopeSmooth(samples, 20);
        
        return new AudioChunkData(
                samples,
                rawAudio.sampleRate,
                0.0, // Peak calculated later by WaveformBuffer if needed
                rawAudio.frameCount,
                rawAudio.overlapFrames);
    }
    
    /**
     * Scales processed audio data to display pixel resolution.
     */
    private double[] scaleToDisplay(AudioChunkData processedAudio, int targetPixelWidth) {
        // Scale to display pixel resolution  
        double[] displayAmplitudes = pixelScaler.toPixelResolution(
                processedAudio.amplitudeValues,
                processedAudio.overlapFrames,
                targetPixelWidth,
                processedAudio.frameCount);
        
        // Final visual smoothing
        pixelScaler.smoothPixels(displayAmplitudes);
        
        return displayAmplitudes;
    }
}