package waveform.signal;

/** Audio chunk with amplitude data and metadata. */
record AudioChunkData(
        double[] amplitudeValues,
        double sampleRate,
        double peakAmplitude,
        int frameCount,
        int overlapFrames) {}
