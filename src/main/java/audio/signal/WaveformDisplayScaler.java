package audio.signal;

import graphics.WaveformScaler;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Primary interface for external consumers needing waveform display data.
 * 
 * Orchestrates pure audio processing and display scaling in a single call.
 * Consumers only interact with this class - the audio processing dependency
 * is handled internally.
 */
@Singleton
public class WaveformDisplayScaler {
    private static final Logger logger = LoggerFactory.getLogger(WaveformDisplayScaler.class);
    
    private final AudioChunkProcessor audioProcessor;
    private final Resampler resampler;
    private final WaveformScaler waveformScaler;
    
    @Inject
    public WaveformDisplayScaler(
            AudioChunkProcessor audioProcessor,
            Resampler resampler,
            WaveformScaler waveformScaler) {
        this.audioProcessor = audioProcessor;
        this.resampler = resampler;
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
    public double[] scaleForDisplay(
            String audioFilePath,
            int chunkIndex,
            double chunkDurationSeconds,
            double overlapSeconds, 
            FrequencyRange frequencyFilter,
            int targetPixelWidth) {
            
        try {
            // Get pure audio domain data
            AudioChunkData audioData = audioProcessor.processChunk(
                    audioFilePath, chunkIndex, chunkDurationSeconds, overlapSeconds, frequencyFilter);
            
            // Scale to display pixel resolution  
            double[] displayAmplitudes = resampler.downsample(
                    audioData.amplitudeValues,
                    audioData.overlapFrames,
                    targetPixelWidth,
                    audioData.frameCount);
            
            // Final visual smoothing
            resampler.smoothPixels(displayAmplitudes);
            
            return displayAmplitudes;
            
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
}