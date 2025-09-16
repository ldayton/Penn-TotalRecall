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

    /** Calculate optimal cache size based on viewport and prefetch requirements. */
    private int calculateCacheSize(int viewportWidthPx) {
        int visibleSegments = (int) Math.ceil((double) viewportWidthPx / SEGMENT_WIDTH_PX);
        int prefetchSegments = PREFETCH_COUNT * 2; // Prefetch in both directions

        // Calculate cache size with buffer for concurrent operations
        // Need space for: visible segments + prefetch segments + buffer for async operations
        // Add 50% buffer to prevent thrashing during concurrent rendering
        int cacheSize = (int) ((visibleSegments + prefetchSegments) * 1.5);

        // Ensure minimum cache size to handle edge cases
        return Math.max(cacheSize, visibleSegments + prefetchSegments + 10);
    }

    void initialize(@NonNull WaveformViewportSpec viewport) {
        int visibleSegments =
                (int) Math.ceil((double) viewport.viewportWidthPx() / SEGMENT_WIDTH_PX);
        int prefetchSegments = PREFETCH_COUNT * 2; // Prefetch in both directions

        this.size = calculateCacheSize(viewport.viewportWidthPx());
        this.entries = new CacheEntry[size];
        this.currentViewport = viewport;

        log.debug(
                "Initialized cache with size {} (visible: {}, prefetch: {}, buffer: {})",
                size,
                visibleSegments,
                prefetchSegments,
                size - visibleSegments - prefetchSegments);
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
            if (entries == null) {
                if (recordStats) {
                    stats.recordMiss();
                    log.warn(
                            "Cache not initialized, returning null for segment {} ({}s at {}pps)",
                            key.segmentIndex(),
                            key.startTime(),
                            key.pixelsPerSecond());
                }
                return null;
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
            if (entries == null) {
                log.warn("Cache not initialized, cannot put segment {}", key.segmentIndex());
                return;
            }
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
                log.debug(
                        "Evicting segment {} to make room for segment {}",
                        entries[head].key().segmentIndex(),
                        key.segmentIndex());
                stats.recordEviction();
            }
            entries[head] = new CacheEntry(key, future);
            log.debug(
                    "Put segment {} at position {} (cache size: {})",
                    key.segmentIndex(),
                    head,
                    size);
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
            if (currentViewport == null) {
                // First time initialization
                initialize(newViewport);
                return;
            }
            if (currentViewport.pixelsPerSecond() != newViewport.pixelsPerSecond()
                    || currentViewport.viewportHeightPx() != newViewport.viewportHeightPx()) {
                log.debug(
                        "Clearing cache due to viewport change: pps {} -> {}, height {} -> {}",
                        currentViewport.pixelsPerSecond(),
                        newViewport.pixelsPerSecond(),
                        currentViewport.viewportHeightPx(),
                        newViewport.viewportHeightPx());
                clear();
                initialize(newViewport);
                return;
            } else if (currentViewport.viewportWidthPx() != newViewport.viewportWidthPx()) {
                log.debug(
                        "Resizing cache due to viewport width change: {} -> {}",
                        currentViewport.viewportWidthPx(),
                        newViewport.viewportWidthPx());
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
        int newSize = calculateCacheSize(newViewportWidthPx);

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
