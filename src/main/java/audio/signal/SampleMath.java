package audio.signal;

/**
 * Basic mathematical operations for audio sample processing.
 *
 * <p>This class provides fundamental calculations needed throughout audio processing,
 * following standard audio engineering formulas and conventions.
 */
public class SampleMath {

    /**
     * Converts audio frames to byte count for file I/O operations.
     *
     * @param frames number of audio frames
     * @param bytesPerFrame size of each frame in bytes (channels × bits/8)
     * @return total byte count
     */
    public long framesToBytes(long frames, int bytesPerFrame) {
        if (frames < 0) {
            throw new IllegalArgumentException("Frame count cannot be negative: " + frames);
        }
        if (bytesPerFrame <= 0) {
            throw new IllegalArgumentException("Bytes per frame must be > 0: " + bytesPerFrame);
        }
        
        return frames * bytesPerFrame;
    }

    /**
     * Converts time duration to frame count using sample rate.
     *
     * @param seconds duration in seconds
     * @param sampleRate audio sample rate in Hz
     * @return number of audio frames
     */
    public long secondsToFrames(double seconds, double sampleRate) {
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("Sample rate must be > 0: " + sampleRate);
        }
        
        return Math.round(seconds * sampleRate);
    }

    /**
     * Converts frame count to time duration using sample rate.
     *
     * @param frames number of audio frames
     * @param sampleRate audio sample rate in Hz
     * @return duration in seconds
     */
    public double framesToSeconds(long frames, double sampleRate) {
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("Sample rate must be > 0: " + sampleRate);
        }
        
        return (double) frames / sampleRate;
    }

    /**
     * Calculates byte offset for a specific chunk in chunked audio streaming.
     *
     * @param chunkIndex zero-based chunk number
     * @param chunkSizeSeconds duration of each chunk in seconds
     * @param sampleRate audio sample rate in Hz
     * @param bytesPerFrame size of each audio frame in bytes
     * @return byte offset from start of file
     */
    public long getChunkOffset(int chunkIndex, double chunkSizeSeconds, 
                              double sampleRate, int bytesPerFrame) {
        if (chunkIndex < 0) {
            throw new IllegalArgumentException("Chunk index cannot be negative: " + chunkIndex);
        }
        if (chunkSizeSeconds <= 0) {
            throw new IllegalArgumentException("Chunk size must be > 0: " + chunkSizeSeconds);
        }
        
        long framesPerChunk = secondsToFrames(chunkSizeSeconds, sampleRate);
        long totalFrames = (long) chunkIndex * framesPerChunk;
        
        return framesToBytes(totalFrames, bytesPerFrame);
    }

    /**
     * Calculates overlap frames for signal processing context.
     *
     * <p>Overlap provides context for operations that need to look backwards
     * in time (like filters with impulse responses).
     *
     * @param overlapSeconds duration of overlap in seconds
     * @param sampleRate audio sample rate in Hz
     * @return number of frames to include as overlap
     */
    public long getOverlapFrames(double overlapSeconds, double sampleRate) {
        if (overlapSeconds < 0) {
            throw new IllegalArgumentException("Overlap duration cannot be negative: " + overlapSeconds);
        }
        
        return secondsToFrames(overlapSeconds, sampleRate);
    }

    /**
     * Calculates bytes per frame from audio format parameters.
     *
     * @param channels number of audio channels
     * @param bitsPerSample bits per sample
     * @return bytes per audio frame
     */
    public int getBytesPerFrame(int channels, int bitsPerSample) {
        if (channels <= 0) {
            throw new IllegalArgumentException("Channel count must be > 0: " + channels);
        }
        if (bitsPerSample <= 0 || bitsPerSample % 8 != 0) {
            throw new IllegalArgumentException("Bits per sample must be positive multiple of 8: " + bitsPerSample);
        }
        
        return channels * (bitsPerSample / 8);
    }

    /**
     * Validates audio format parameters for consistency.
     *
     * @param sampleRate sample rate in Hz
     * @param channels number of audio channels
     * @param bitsPerSample bits per sample (8, 16, 24, 32)
     * @throws IllegalArgumentException if any parameter is invalid
     */
    public void validateAudioFormat(double sampleRate, int channels, int bitsPerSample) {
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("Sample rate must be > 0: " + sampleRate);
        }
        if (channels <= 0) {
            throw new IllegalArgumentException("Channel count must be > 0: " + channels);
        }
        if (bitsPerSample <= 0 || bitsPerSample % 8 != 0) {
            throw new IllegalArgumentException("Bits per sample must be positive multiple of 8: " + bitsPerSample);
        }
        
        // Sanity check for typical audio ranges
        if (sampleRate < 8000 || sampleRate > 192000) {
            throw new IllegalArgumentException("Sample rate outside typical range (8kHz-192kHz): " + sampleRate);
        }
    }
}