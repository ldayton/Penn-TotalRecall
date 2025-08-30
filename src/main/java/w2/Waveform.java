package w2;

import java.awt.Image;
import java.util.concurrent.CompletableFuture;

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
    CompletableFuture<Image> renderViewport(ViewportContext viewport);

    /** Create waveform for audio file. */
    static Waveform forAudioFile(String audioFilePath) {
        return WaveformImpl.create(audioFilePath);
    }
}
