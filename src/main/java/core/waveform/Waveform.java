package core.waveform;

import core.audio.AudioEngine;
import core.audio.AudioHandle;
import core.audio.AudioMetadata;
import core.audio.SampleReader;
import java.awt.Image;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Viewport-aware waveform renderer with segment caching. Thread-safe implementation that manages
 * parallel rendering.
 */
@Slf4j
public class Waveform {

    private final WaveformRenderer renderer;
    private final WaveformSegmentCache cache;
    private final ExecutorService renderPool;
    private final ScheduledExecutorService statsTimer;
    private final SampleReader sampleReader;

    public Waveform(
            @NonNull String audioFilePath,
            @NonNull AudioEngine audioEngine,
            @NonNull AudioHandle audioHandle,
            @NonNull SampleReader sampleReader,
            @NonNull WaveformSegmentCache cache) {

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
        WaveformViewportSpec defaultViewport = new WaveformViewportSpec(0.0, 10.0, 1000, 200, 100);

        this.cache = cache;
        this.cache.initialize(defaultViewport);

        // Get sample rate from audio metadata
        AudioMetadata metadata = audioEngine.getMetadata(audioHandle);
        int sampleRate = metadata.sampleRate();

        this.sampleReader = sampleReader;

        this.renderer =
                new WaveformRenderer(audioFilePath, cache, renderPool, sampleReader, sampleRate);

        // Create timer for periodic stats logging
        this.statsTimer =
                Executors.newSingleThreadScheduledExecutor(
                        r -> {
                            Thread t = new Thread(r, "WaveformStatsTimer");
                            t.setDaemon(true);
                            return t;
                        });

        // Schedule stats logging every second
        statsTimer.scheduleAtFixedRate(
                () -> {
                    CacheStats stats = cache.getStats();
                    if (stats.getRequests() > 0) {
                        log.debug("Waveform cache stats: {}", stats);
                    }
                },
                1,
                1,
                TimeUnit.SECONDS);
    }

    public CompletableFuture<Image> renderViewport(@NonNull WaveformViewportSpec viewport) {
        if (renderer == null) {
            return CompletableFuture.completedFuture(null);
        }
        try {
            CompletableFuture<Image> result = renderer.renderViewport(viewport);
            return result;
        } catch (Exception e) {
            // Log error but don't rethrow
            return CompletableFuture.completedFuture(null);
        }
    }

    /** Shutdown the renderer and release resources. */
    public void shutdown() {
        // Stop stats timer
        statsTimer.shutdown();

        // Log final cache stats before shutdown
        CacheStats stats = cache.getStats();
        if (stats.getRequests() > 0) {
            log.info("Waveform cache final stats: {}", stats);
            stats.reset(); // Reset for next waveform
        }

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

        // Close the sample reader
        try {
            if (sampleReader != null) {
                sampleReader.close();
            }
        } catch (Exception e) {
            // Log but don't rethrow
            log.error("Error closing sample reader", e);
        }
    }
}
