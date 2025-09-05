package w2;

import a2.AudioEngine;
import a2.AudioHandle;
import a2.AudioMetadata;
import java.awt.Image;
import java.util.concurrent.*;
import lombok.NonNull;

/**
 * Viewport-aware waveform renderer with segment caching. Thread-safe implementation that manages
 * parallel rendering.
 */
public class Waveform {

    private final WaveformRenderer renderer;
    private final WaveformSegmentCache cache;
    private final ExecutorService renderPool;

    public Waveform(
            @NonNull String audioFilePath,
            @NonNull AudioEngine audioEngine,
            @NonNull AudioHandle audioHandle) {
        // Create thread pool for rendering (leave 1 core for UI)
        int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        this.renderPool =
                Executors.newFixedThreadPool(
                        threads,
                        new ThreadFactory() {
                            private int counter = 0;

                            @Override
                            public Thread newThread(@NonNull Runnable r) {
                                Thread t = new Thread(r);
                                t.setName("WaveformRenderer-" + counter++);
                                t.setDaemon(true);
                                return t;
                            }
                        });

        // Initialize with default viewport (will be updated on first render)
        ViewportContext defaultViewport =
                new ViewportContext(
                        0.0, 10.0, 1000, 200, 100, ViewportContext.ScrollDirection.FORWARD);

        this.cache = new WaveformSegmentCache(defaultViewport);

        // Get sample rate from audio metadata
        AudioMetadata metadata = audioEngine.getMetadata(audioHandle);
        int sampleRate = metadata.sampleRate();
        this.renderer =
                new WaveformRenderer(
                        audioFilePath, cache, renderPool, audioEngine, audioHandle, sampleRate);
    }

    public CompletableFuture<Image> renderViewport(@NonNull ViewportContext viewport) {
        return renderer.renderViewport(viewport);
    }

    /** Shutdown the renderer and release resources. */
    public void shutdown() {
        // Cancel all pending renders
        cache.clear();

        // Shutdown thread pool
        renderPool.shutdown();
        try {
            if (!renderPool.awaitTermination(5, TimeUnit.SECONDS)) {
                renderPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            renderPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
