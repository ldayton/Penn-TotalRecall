package w2;

import audio.FmodCore;
import java.awt.Image;
import java.util.concurrent.*;

/** Implementation of viewport-aware waveform renderer with segment caching. */
class WaveformImpl implements Waveform {

    private final WaveformRenderer renderer;
    private final WaveformSegmentCache cache;
    private final ExecutorService renderPool;
    private final String audioFilePath;
    private final FmodCore fmodCore;

    WaveformImpl(String audioFilePath, FmodCore fmodCore) {
        this.audioFilePath = audioFilePath;
        this.fmodCore = fmodCore;

        // Create thread pool for rendering (leave 1 core for UI)
        int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        this.renderPool =
                Executors.newFixedThreadPool(
                        threads,
                        new ThreadFactory() {
                            private int counter = 0;

                            @Override
                            public Thread newThread(Runnable r) {
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

        // TODO Get sample rate from audio file for proper rendering
        int sampleRate = 44100; // Default, should get from FMOD
        this.renderer =
                new WaveformRenderer(audioFilePath, cache, renderPool, fmodCore, sampleRate);
    }

    @Override
    public CompletableFuture<Image> renderViewport(ViewportContext viewport) {
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
    public static Waveform create(String audioFilePath, FmodCore fmodCore) {
        return new WaveformImpl(audioFilePath, fmodCore);
    }
}
