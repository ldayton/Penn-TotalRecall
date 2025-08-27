package components.waveform;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import state.AudioState;
import waveform.Waveform;
import waveform.WaveformChunk;

/** LRU cache for waveform chunks with async prefetching. */
@Singleton
public class WaveformChunkCache {
    /** Audio chunk size in seconds for waveform buffering. */
    public static final int CHUNK_SIZE_SECONDS = 10;
    /** Cache size: current + previous + next chunk. */
    private static final int MAX_CACHE_SIZE = 3;

    private final AudioState audioState;
    private final LoadingCache<ChunkKey, WaveformChunk> chunkCache;

    /** Cache key: chunk number + render height. */
    public record ChunkKey(int chunkNumber, int height) {}

    @Inject
    public WaveformChunkCache(AudioState audioState) {
        this.audioState = audioState;
        this.chunkCache = Caffeine.newBuilder()
                .maximumSize(MAX_CACHE_SIZE)
                .build(this::loadChunk);
    }

    /** Get chunk with async prefetching of adjacent chunks. */
    public WaveformChunk getChunk(int chunkNumber, int height) {
        requireAudioLoaded();
        var key = new ChunkKey(chunkNumber, height);
        var chunk = chunkCache.get(key);
        prefetchAdjacentAsync(chunkNumber, height);
        return chunk;
    }

    /** Clears all cached chunks. */
    public void clear() {
        chunkCache.invalidateAll();
    }

    /** Get multiple chunks for bulk operations. */
    public List<WaveformChunk> getChunks(List<ChunkKey> keys) {
        return chunkCache.getAll(keys).values().stream().toList();
    }

    /** Load chunk from waveform data (called by Caffeine cache loader). */
    private WaveformChunk loadChunk(ChunkKey key) {
        var waveform = audioState.getCurrentWaveform();
        if (waveform == null) {
            throw new IllegalStateException("No audio file loaded - cannot render waveform chunks");
        }
        return new WaveformChunk(waveform, key.chunkNumber, key.height);
    }

    /** Ensure audio is loaded before cache operations. */
    private void requireAudioLoaded() {
        if (audioState.getCurrentWaveform() == null) {
            throw new IllegalStateException("No audio file loaded - cannot render waveform chunks");
        }
    }

    /** Async prefetch adjacent chunks (previous/next) without blocking. */
    private void prefetchAdjacentAsync(int chunkNumber, int height) {
        var maxChunk = audioState.lastChunkNum();
        var adjacentKeys = Stream.of(
                chunkNumber > 0 ? new ChunkKey(chunkNumber - 1, height) : null,
                chunkNumber < maxChunk ? new ChunkKey(chunkNumber + 1, height) : null
        ).filter(java.util.Objects::nonNull)
         .toList();

        if (!adjacentKeys.isEmpty()) {
            CompletableFuture.runAsync(() -> chunkCache.getAll(adjacentKeys));
        }
    }
}
