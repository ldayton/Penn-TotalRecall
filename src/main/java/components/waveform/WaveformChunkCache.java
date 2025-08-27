package components.waveform;

import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import state.AudioState;
import waveform.Waveform;
import waveform.WaveformChunk;

/** Modern LRU cache for waveform chunks with async prefetching and LoadingCache pattern. */
@Singleton
public class WaveformChunkCache {
    /** Audio chunk size in seconds for waveform buffering. */
    public static final int CHUNK_SIZE_SECONDS = 10;
    private static final int MAX_CACHE_SIZE = 3; // current + previous + next

    private final AudioState audioState;
    private final LoadingCache<ChunkKey, WaveformChunk> chunkCache;

    /** Cache key combining chunk number and render height for proper invalidation. */
    public record ChunkKey(int chunkNumber, int height) {}

    @Inject
    public WaveformChunkCache(AudioState audioState) {
        this.audioState = audioState;
        this.chunkCache = Caffeine.newBuilder()
                .maximumSize(MAX_CACHE_SIZE)
                .build(this::loadChunk);
    }

    /** Gets chunk with async read-ahead prefetching of adjacent chunks. */
    public WaveformChunk getChunk(int chunkNumber, int height) {
        requireAudioLoaded();
        
        ChunkKey key = new ChunkKey(chunkNumber, height);
        WaveformChunk chunk = chunkCache.get(key);
        
        // Async prefetch adjacent chunks without blocking
        prefetchAdjacentAsync(chunkNumber, height);
        
        return chunk;
    }

    /** Clears all cached chunks. */
    public void clear() {
        chunkCache.invalidateAll();
    }

    /** Gets multiple chunks efficiently for bulk operations. */
    public List<WaveformChunk> getChunks(List<ChunkKey> keys) {
        return chunkCache.getAll(keys).values().stream().toList();
    }

    private WaveformChunk loadChunk(ChunkKey key) {
        Waveform currentWaveform = audioState.getCurrentWaveform();
        if (currentWaveform == null) {
            throw new IllegalStateException("No audio file loaded - cannot render waveform chunks");
        }
        return new WaveformChunk(currentWaveform, key.chunkNumber, key.height);
    }

    private void requireAudioLoaded() {
        if (audioState.getCurrentWaveform() == null) {
            throw new IllegalStateException("No audio file loaded - cannot render waveform chunks");
        }
    }

    /** Async prefetch adjacent chunks without blocking main thread. */
    private void prefetchAdjacentAsync(int chunkNumber, int height) {
        int maxChunk = audioState.lastChunkNum();
        
        // Build list of adjacent chunks to prefetch
        var adjacentKeys = List.of(
            chunkNumber > 0 ? new ChunkKey(chunkNumber - 1, height) : null,
            chunkNumber < maxChunk ? new ChunkKey(chunkNumber + 1, height) : null
        ).stream()
         .filter(key -> key != null)
         .toList();

        // Async prefetch - don't block for results
        if (!adjacentKeys.isEmpty()) {
            CompletableFuture.runAsync(() -> chunkCache.getAll(adjacentKeys));
        }
    }
}
