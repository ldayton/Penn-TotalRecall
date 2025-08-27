package audio.signal;

import audio.FmodCore;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import marytts.signalproc.filter.BandPassFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pure audio domain processor - no display or pixel concepts.
 * 
 * Processes raw audio files into amplitude data using FMOD loading, frequency filtering,
 * and signal enhancement. Results are in pure audio domain (samples, frequencies, time).
 * Package-private - only used internally by WaveformDisplayScaler.
 */
@Singleton
class AudioChunkProcessor {
    private static final Logger logger = LoggerFactory.getLogger(AudioChunkProcessor.class);
    
    private final FmodCore fmodCore;
    private final AudioRenderer audioRenderer;
    
    @Inject
    public AudioChunkProcessor(FmodCore fmodCore, AudioRenderer audioRenderer) {
        this.fmodCore = fmodCore;
        this.audioRenderer = audioRenderer;
    }
    
    /**
     * Processes audio chunk into pure audio domain data.
     * Package-private - only called by WaveformDisplayScaler.
     */
    AudioChunkData processChunk(
            String audioFilePath,
            int chunkIndex,
            double chunkDurationSeconds,
            double overlapSeconds,
            FrequencyRange frequencyFilter) throws IOException {
            
        // Load raw audio chunk using FMOD
        FmodCore.ChunkData chunkData = fmodCore.readAudioChunk(
                audioFilePath, chunkIndex, chunkDurationSeconds, overlapSeconds);
        
        double[] samples = chunkData.samples.clone();
        int overlapFrames = chunkData.overlapFrames;
        
        // Apply frequency filtering if samples exist
        if (samples.length > 0) {
            BandPassFilter filter = new BandPassFilter(
                    frequencyFilter.minFrequency, 
                    frequencyFilter.maxFrequency);
            samples = filter.apply(samples);
        }
        
        // Apply signal enhancement
        audioRenderer.envelopeSmooth(samples, 20);
        
        // Calculate peak amplitude for reference
        double peakAmplitude = audioRenderer.getRenderingPeak(samples, samples.length / 2);
        
        return new AudioChunkData(
                samples,
                chunkData.sampleRate,
                peakAmplitude,
                chunkData.totalFrames,
                overlapFrames);
    }
}