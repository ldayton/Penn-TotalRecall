package waveform;

import java.awt.Image;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import lombok.NonNull;

/**
 * Circular cache for 200px-wide waveform segments. Optimized for timeline scrolling with
 * fixed-width segment compositing.
 *
 * <p>Thread-safe.
 */
class WaveformSegmentCache {

    private static final int SEGMENT_WIDTH_PX = 200;

    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private CacheEntry[] entries;
    private int size;
    private int head = 0;
    private ViewportContext currentViewport;

    record CacheEntry(@NonNull SegmentKey key, @NonNull CompletableFuture<Image> future) {}

    record SegmentKey(double startTime, int pixelsPerSecond, int height) {
        double duration() {
            return (double) SEGMENT_WIDTH_PX / pixelsPerSecond;
        }

        double endTime() {
            return startTime + duration();
        }
    }

    WaveformSegmentCache(@NonNull ViewportContext viewport) {
        int visibleSegments =
                (int) Math.ceil((double) viewport.viewportWidthPx() / SEGMENT_WIDTH_PX);
        int prefetchSegments = 4; // 2 each direction
        this.size = visibleSegments + prefetchSegments;
        this.entries = new CacheEntry[size];
        this.currentViewport = viewport;
    }

    /** Get cached segment or null if not found. */
    CompletableFuture<Image> get(@NonNull SegmentKey key) {
        lock.readLock().lock();
        try {
            for (CacheEntry entry : entries) {
                if (entry != null && entry.key().equals(key)) {
                    return entry.future();
                }
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
                    return;
                }
            }

            // Add new entry at head position
            entries[head] = new CacheEntry(key, future);
            head = (head + 1 >= size) ? 0 : head + 1;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Clear all cached segments. */
    void clear() {
        lock.writeLock().lock();
        try {
            for (int i = 0; i < entries.length; i++) {
                if (entries[i] != null) {
                    entries[i].future().cancel(true);
                    entries[i] = null;
                }
            }
            head = 0;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Update cache for new viewport context. Clears cache if pixelsPerSecond or height changed,
     * resizes if width changed.
     */
    void updateViewport(@NonNull ViewportContext newViewport) {
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
        int prefetchSegments = 4; // 2 each direction
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
    }
}
