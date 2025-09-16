package core.waveform;

import com.google.errorprone.annotations.ThreadSafe;
import com.google.inject.Inject;
import java.awt.Image;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Circular cache for 200px-wide waveform segments. Optimized for timeline scrolling with
 * fixed-width segment compositing.
 */
@ThreadSafe
@Slf4j
public class WaveformSegmentCache {

    static final int SEGMENT_WIDTH_PX = 200;
    static final int PREFETCH_COUNT = 10; // Segments to prefetch in each direction

    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private CacheEntry[] entries;
    private int size;
    private int head = 0;
    private WaveformViewportSpec currentViewport;
    private final CacheStats stats;

    record CacheEntry(@NonNull SegmentKey key, @NonNull CompletableFuture<Image> future) {}

    record SegmentKey(long segmentIndex, int pixelsPerSecond, int height) {
        double duration() {
            return (double) SEGMENT_WIDTH_PX / pixelsPerSecond;
        }

        double startTime() {
            // Calculate start time from segment index
            return (segmentIndex * SEGMENT_WIDTH_PX) / (double) pixelsPerSecond;
        }

        double endTime() {
            return startTime() + duration();
        }
    }

    @Inject
    WaveformSegmentCache(@NonNull CacheStats stats) {
        this.stats = stats;
    }

    void initialize(@NonNull WaveformViewportSpec viewport) {
        int visibleSegments =
                (int) Math.ceil((double) viewport.viewportWidthPx() / SEGMENT_WIDTH_PX);
        int prefetchSegments = PREFETCH_COUNT * 2; // Prefetch in both directions
        this.size = visibleSegments + prefetchSegments;
        this.entries = new CacheEntry[size];
        this.currentViewport = viewport;
    }

    /** Get cached segment or null if not found. */
    CompletableFuture<Image> get(@NonNull SegmentKey key) {
        return get(key, true);
    }

    /** Get cached segment or null if not found, optionally recording stats. */
    CompletableFuture<Image> get(@NonNull SegmentKey key, boolean recordStats) {
        lock.readLock().lock();
        try {
            if (recordStats) {
                stats.recordRequest();
            }
            for (CacheEntry entry : entries) {
                if (entry != null && entry.key().equals(key)) {
                    if (recordStats) {
                        stats.recordHit();
                        log.trace(
                                "Cache HIT for segment {} ({}s at {}pps)",
                                key.segmentIndex(),
                                key.startTime(),
                                key.pixelsPerSecond());
                    }
                    return entry.future();
                }
            }
            if (recordStats) {
                stats.recordMiss();
                log.warn(
                        "Cache MISS for segment {} ({}s at {}pps)",
                        key.segmentIndex(),
                        key.startTime(),
                        key.pixelsPerSecond());
            }
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Add segment to cache, overwriting oldest entry if full. */
    void put(@NonNull SegmentKey key, @NonNull CompletableFuture<Image> future) {
        lock.writeLock().lock();
        try {
            // Check if key already exists
            for (int i = 0; i < entries.length; i++) {
                if (entries[i] != null && entries[i].key().equals(key)) {
                    entries[i] = new CacheEntry(key, future);
                    stats.recordUpdate();
                    return;
                }
            }

            // Add new entry at head position
            if (entries[head] != null) {
                stats.recordEviction();
            }
            entries[head] = new CacheEntry(key, future);
            head = (head + 1 >= size) ? 0 : head + 1;
            stats.recordPut();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Clear all cached segments. */
    void clear() {
        lock.writeLock().lock();
        try {
            int cleared = 0;
            for (int i = 0; i < entries.length; i++) {
                if (entries[i] != null) {
                    entries[i].future().cancel(true);
                    entries[i] = null;
                    cleared++;
                }
            }
            head = 0;
            stats.recordClear(cleared);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Update cache for new viewport context. Clears cache if pixelsPerSecond or height changed,
     * resizes if width changed.
     */
    void updateViewport(@NonNull WaveformViewportSpec newViewport) {
        lock.writeLock().lock();
        try {
            if (currentViewport.pixelsPerSecond() != newViewport.pixelsPerSecond()
                    || currentViewport.viewportHeightPx() != newViewport.viewportHeightPx()) {
                clear();
            } else if (currentViewport.viewportWidthPx() != newViewport.viewportWidthPx()) {
                resize(newViewport.viewportWidthPx());
            }
            currentViewport = newViewport;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Resize cache array to accommodate new viewport width. Preserves existing valid segments. */
    private void resize(int newViewportWidthPx) {
        // Called from updateViewport() which already holds write lock
        int visibleSegments = (int) Math.ceil((double) newViewportWidthPx / SEGMENT_WIDTH_PX);
        int prefetchSegments = PREFETCH_COUNT * 2; // Prefetch in both directions
        int newSize = visibleSegments + prefetchSegments;

        if (newSize == size) {
            return; // No resize needed
        }

        CacheEntry[] newEntries = new CacheEntry[newSize];

        // Copy existing valid entries
        int copied = 0;
        for (CacheEntry entry : entries) {
            if (entry != null && copied < newSize) {
                newEntries[copied++] = entry;
            }
        }

        entries = newEntries;
        size = newSize;
        head = copied % newSize;
        stats.recordResize(size, newSize);
    }

    /** Get cache statistics. */
    public CacheStats getStats() {
        return stats;
    }
}
