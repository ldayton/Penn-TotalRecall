package w2;

import java.awt.Image;
import java.util.concurrent.CompletableFuture;

/**
 * Circular cache for 200px-wide waveform segments. Optimized for timeline scrolling with
 * fixed-width segment compositing.
 */
class WaveformSegmentCache {

    private static final int SEGMENT_WIDTH_PX = 200;

    private final CacheEntry[] entries;
    private final int size;
    private int head = 0;

    record CacheEntry(SegmentKey key, CompletableFuture<Image> future) {}

    record SegmentKey(double startTime, int pixelsPerSecond, int height) {
        double duration() {
            return (double) SEGMENT_WIDTH_PX / pixelsPerSecond;
        }

        double endTime() {
            return startTime + duration();
        }
    }

    WaveformSegmentCache(int size) {
        throw new UnsupportedOperationException("Implementation needed");
    }

    /** Get cached segment or null if not found. */
    CompletableFuture<Image> get(SegmentKey key) {
        throw new UnsupportedOperationException("Implementation needed");
    }

    /** Add segment to cache, overwriting oldest entry if full. */
    void put(SegmentKey key, CompletableFuture<Image> future) {
        throw new UnsupportedOperationException("Implementation needed");
    }

    /** Clear all cached segments. */
    void clear() {
        throw new UnsupportedOperationException("Implementation needed");
    }
}
