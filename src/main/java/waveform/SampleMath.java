package waveform;

/** Mathematical operations for audio sample processing. */
final class SampleMath {

    /** Converts audio frames to byte count for file I/O operations. */
    public long framesToBytes(long frames, int bytesPerFrame) {
        if (frames < 0) {
            throw new IllegalArgumentException("Frame count cannot be negative: " + frames);
        }
        if (bytesPerFrame <= 0) {
            throw new IllegalArgumentException("Bytes per frame must be > 0: " + bytesPerFrame);
        }
        return frames * bytesPerFrame;
    }

    /** Converts time duration to frame count using sample rate. */
    public long secondsToFrames(double seconds, double sampleRate) {
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("Sample rate must be > 0: " + sampleRate);
        }
        return Math.round(seconds * sampleRate);
    }

    /** Converts frame count to time duration using sample rate. */
    public double framesToSeconds(long frames, double sampleRate) {
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("Sample rate must be > 0: " + sampleRate);
        }
        return (double) frames / sampleRate;
    }

    /** Calculates byte offset for a specific chunk in chunked audio streaming. */
    public long getChunkOffset(
            int chunkIndex, double chunkSizeSeconds, double sampleRate, int bytesPerFrame) {
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

    /** Calculates overlap frames for signal processing context. */
    public long getOverlapFrames(double overlapSeconds, double sampleRate) {
        if (overlapSeconds < 0) {
            throw new IllegalArgumentException(
                    "Overlap duration cannot be negative: " + overlapSeconds);
        }
        return secondsToFrames(overlapSeconds, sampleRate);
    }

    /** Calculates bytes per frame from audio format parameters. */
    public int getBytesPerFrame(int channels, int bitsPerSample) {
        if (channels <= 0) {
            throw new IllegalArgumentException("Channel count must be > 0: " + channels);
        }
        if (bitsPerSample <= 0 || bitsPerSample % 8 != 0) {
            throw new IllegalArgumentException(
                    "Bits per sample must be positive multiple of 8: " + bitsPerSample);
        }
        return channels * (bitsPerSample / 8);
    }

    /** Validates audio format parameters for consistency. */
    public void validateAudioFormat(double sampleRate, int channels, int bitsPerSample) {
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("Sample rate must be > 0: " + sampleRate);
        }
        if (channels <= 0) {
            throw new IllegalArgumentException("Channel count must be > 0: " + channels);
        }
        if (bitsPerSample <= 0 || bitsPerSample % 8 != 0) {
            throw new IllegalArgumentException(
                    "Bits per sample must be positive multiple of 8: " + bitsPerSample);
        }

        // Sanity check for typical audio ranges
        if (sampleRate < 8000 || sampleRate > 192000) {
            throw new IllegalArgumentException(
                    "Sample rate outside typical range (8kHz-192kHz): " + sampleRate);
        }
    }
}