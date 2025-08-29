package waveform;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import state.AudioState;

/** LRU cache for waveform chunks with async prefetching. */
final class WaveformChunkCache {

    /** Cache size: current + previous + next chunk. */
    private static final int MAX_CACHE_SIZE = 3;

    private final Waveform waveform;
    private final AudioState audioState;
    private final LoadingCache<ChunkKey, RenderedChunk> chunkCache;

    /** Cache key includes all render-affecting inputs. */
    public record ChunkKey(int chunkNumber, int pxPerSec, int height) {}

    public WaveformChunkCache(Waveform waveform, AudioState audioState) {
        this.waveform = waveform;
        this.audioState = audioState;
        this.chunkCache = Caffeine.newBuilder().maximumSize(MAX_CACHE_SIZE).build(this::loadChunk);
    }

    /** Get chunk with async prefetching of adjacent chunks. */
    public RenderedChunk getChunk(int chunkNumber, int pxPerSec, int height) {
        requireAudioLoaded();
        var key = new ChunkKey(chunkNumber, pxPerSec, height);
        var chunk = chunkCache.get(key);
        prefetchAdjacentAsync(chunkNumber, pxPerSec, height);
        return chunk;
    }

    /** Clears all cached chunks. */
    public void clear() {
        chunkCache.invalidateAll();
    }

    /** Load chunk from waveform data (called by Caffeine cache loader). */
    private RenderedChunk loadChunk(ChunkKey key) {
        var image = waveform.renderChunkDirectConfigured(key.chunkNumber, key.pxPerSec, key.height);
        return new RenderedChunk(key.chunkNumber, image);
    }

    /** Ensure audio is loaded before cache operations. */
    private void requireAudioLoaded() {
        if (!audioState.audioOpen()) {
            throw new IllegalStateException("No audio file loaded - cannot render waveform chunks");
        }
    }

    /** Async prefetch adjacent chunks (previous/next) without blocking. */
    private void prefetchAdjacentAsync(int chunkNumber, int pxPerSec, int height) {
        var maxChunk = audioState.lastChunkNum();
        var adjacentKeys =
                Stream.of(
                                chunkNumber > 0
                                        ? new ChunkKey(chunkNumber - 1, pxPerSec, height)
                                        : null,
                                chunkNumber < maxChunk
                                        ? new ChunkKey(chunkNumber + 1, pxPerSec, height)
                                        : null)
                        .filter(java.util.Objects::nonNull)
                        .toList();

        if (!adjacentKeys.isEmpty()) {
            CompletableFuture.runAsync(() -> chunkCache.getAll(adjacentKeys));
        }
    }
}
