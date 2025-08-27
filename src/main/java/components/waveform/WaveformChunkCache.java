package components.waveform;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import state.AudioState;
import waveform.Waveform;
import waveform.WaveformChunk;

/** Simple LRU cache for waveform chunks with read-ahead prefetching. */
@Singleton
public class WaveformChunkCache {
    /** Audio chunk size in seconds for waveform buffering. */
    public static final int CHUNK_SIZE_SECONDS = 10;

    private final AudioState audioState;
    private final Cache<ChunkKey, WaveformChunk> chunkCache;

    private record ChunkKey(int chunkNumber, int height) {}

    @Inject
    public WaveformChunkCache(AudioState audioState) {
        this.audioState = audioState;
        this.chunkCache =
                Caffeine.newBuilder()
                        .maximumSize(3) // current + previous + next
                        .build();
    }

    /** Gets chunk with read-ahead prefetching of adjacent chunks. */
    public WaveformChunk getChunk(int chunkNumber, int height) {
        Waveform currentWaveform = audioState.getCurrentWaveform();
        if (currentWaveform == null) {
            throw new IllegalStateException("No audio file loaded - cannot render waveform chunks");
        }

        ChunkKey key = new ChunkKey(chunkNumber, height);
        WaveformChunk chunk =
                chunkCache.get(
                        key, k -> new WaveformChunk(currentWaveform, k.chunkNumber, k.height));

        // Prefetch adjacent chunks
        prefetchAdjacent(chunkNumber, height, currentWaveform);

        return chunk;
    }

    /** Clears all cached chunks. */
    public void clear() {
        chunkCache.invalidateAll();
    }

    private void prefetchAdjacent(int chunkNumber, int height, Waveform waveform) {
        int maxChunk = audioState.lastChunkNum();

        // Prefetch previous chunk if it exists
        if (chunkNumber > 0) {
            ChunkKey prevKey = new ChunkKey(chunkNumber - 1, height);
            chunkCache.get(prevKey, k -> new WaveformChunk(waveform, k.chunkNumber, k.height));
        }

        // Prefetch next chunk if it exists
        if (chunkNumber < maxChunk) {
            ChunkKey nextKey = new ChunkKey(chunkNumber + 1, height);
            chunkCache.get(nextKey, k -> new WaveformChunk(waveform, k.chunkNumber, k.height));
        }
    }
}
