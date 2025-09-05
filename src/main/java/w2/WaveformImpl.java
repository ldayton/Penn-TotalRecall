package w2;

import a2.AudioEngine;
import a2.AudioHandle;
import a2.AudioMetadata;
import java.awt.Image;
import java.util.concurrent.*;
import lombok.NonNull;

/** Implementation of viewport-aware waveform renderer with segment caching. */
class WaveformImpl implements Waveform {

    private final WaveformRenderer renderer;
    private final WaveformSegmentCache cache;
    private final ExecutorService renderPool;

    WaveformImpl(
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

    @Override
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

    /** Factory method to create waveform for audio file. */
    public static Waveform create(
            @NonNull String audioFilePath,
            @NonNull AudioEngine audioEngine,
            @NonNull AudioHandle audioHandle) {
        return new WaveformImpl(audioFilePath, audioEngine, audioHandle);
    }
}
