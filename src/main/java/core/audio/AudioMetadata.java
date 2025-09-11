package core.audio;

import com.google.errorprone.annotations.ThreadSafe;

@ThreadSafe
public record AudioMetadata(
        int sampleRate,
        int channelCount,
        int bitsPerSample,
        String format,
        long frameCount,
        double durationSeconds) {}
