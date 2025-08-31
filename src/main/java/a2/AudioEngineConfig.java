package a2;

import app.annotations.ThreadSafe;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

/**
 * Configuration for audio engine initialization. Immutable configuration object using builder
 * pattern.
 */
@ThreadSafe
@Getter
@ToString
@Builder
public final class AudioEngineConfig {

    /** Mode for audio engine operation. Determines optimization priorities. */
    public enum Mode {
        /** Optimized for real-time playback with low latency. */
        PLAYBACK,

        /** Optimized for waveform rendering and analysis. */
        RENDERING
    }

    @NonNull @Builder.Default private final String engineType = "fmod";

    @NonNull @Builder.Default private final Mode mode = Mode.PLAYBACK;

    @Builder.Default private final long maxCacheBytes = 128L * 1024 * 1024; // 128MB default

    @Builder.Default private final boolean enablePrefetch = true;

    @Builder.Default
    private final int prefetchWindowSeconds = 5; // Prefetch 5 seconds in both directions

    public static AudioEngineConfig defaults() {
        return builder().build();
    }
}
