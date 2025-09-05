package w2;

import a2.AudioEngine;
import a2.AudioHandle;
import java.awt.Image;
import java.util.concurrent.CompletableFuture;
import lombok.NonNull;

/**
 * Async viewport-aware waveform renderer.
 *
 * <p>Based on industry research: DAW waveform rendering must be async with timeout/cancellation to
 * prevent UI blocking and enable user control over long-running operations.
 *
 * <p>Thread-safe.
 */
public interface Waveform {

    /**
     * Render waveform for viewport context asynchronously.
     *
     * <p>Returns immediately with CompletableFuture for timeout/cancellation control:
     *
     * <ul>
     *   <li>Timeout: {@code future.orTimeout(30, TimeUnit.SECONDS)}
     *   <li>Cancellation: {@code future.cancel(true)}
     *   <li>Chaining: {@code future.thenApply(image -> ...)}
     * </ul>
     *
     * <p>Implementation can use viewport info for intelligent caching decisions.
     */
    CompletableFuture<Image> renderViewport(@NonNull ViewportContext viewport);

    /** Create waveform for audio file. */
    static Waveform forAudioFile(
            @NonNull String audioFilePath,
            @NonNull AudioEngine audioEngine,
            @NonNull AudioHandle audioHandle) {
        return WaveformImpl.create(audioFilePath, audioEngine, audioHandle);
    }
}
