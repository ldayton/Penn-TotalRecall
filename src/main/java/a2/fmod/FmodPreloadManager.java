package a2.fmod;

import app.annotations.ThreadSafe;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalListener;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages preloading of audio segments for instant playback. Maintains an LRU cache of
 * memory-loaded audio segments to enable zero-latency playback of recently accessed ranges.
 *
 * <p>This manager is particularly useful for:
 *
 * <ul>
 *   <li>Instant replay of short segments (e.g., last 200ms)
 *   <li>Seamless looping of specific ranges
 *   <li>Reducing latency when jumping between known positions
 * </ul>
 *
 * <p>Important: This manager creates separate FMOD sound objects for each cached segment using
 * FMOD_CREATESAMPLE mode, which loads the entire segment into memory. These are independent from
 * the main streaming sound and must be properly released.
 *
 * <p>The cache automatically evicts least-recently-used segments and properly releases FMOD sound
 * objects to prevent memory leaks.
 */
@ThreadSafe
@Slf4j
class FmodPreloadManager {

    private final FmodLibrary fmod;
    private final Pointer system;
    private final int maxCacheSize;
    private final Cache<PreloadKey, Pointer> cache;
    private final ConcurrentHashMap<Pointer, Long> soundSizes;
    private final AtomicLong totalMemoryBytes;
    private final AtomicBoolean isShutdown;
    private final Executor asyncExecutor;

    /**
     * Creates a new preload manager.
     *
     * @param fmod The FMOD library interface
     * @param system The FMOD system pointer
     * @param maxCacheSize Maximum number of segments to cache
     */
    FmodPreloadManager(@NonNull FmodLibrary fmod, @NonNull Pointer system, int maxCacheSize) {
        this.fmod = fmod;
        this.system = system;
        this.maxCacheSize = maxCacheSize;
        this.soundSizes = new ConcurrentHashMap<>();
        this.totalMemoryBytes = new AtomicLong(0);
        this.isShutdown = new AtomicBoolean(false);
        this.asyncExecutor = ForkJoinPool.commonPool();

        // Initialize cache with LRU eviction and automatic FMOD cleanup
        this.cache =
                Caffeine.newBuilder()
                        .maximumSize(maxCacheSize)
                        .removalListener(
                                (RemovalListener<PreloadKey, Pointer>)
                                        (key, sound, cause) -> {
                                            if (sound != null) {
                                                releaseSound(sound, key);
                                            }
                                        })
                        .build();

        log.debug("FmodPreloadManager initialized with cache size: {}", maxCacheSize);
    }

    /**
     * Asynchronously preload an audio segment into memory. Creates a new FMOD sound object with
     * FMOD_CREATESAMPLE mode for the entire file, then caches it indexed by the specific range for
     * instant playback.
     *
     * @param filePath The audio file path
     * @param startFrame Starting frame (inclusive)
     * @param endFrame Ending frame (exclusive)
     * @return A future that completes when preloading is done
     */
    CompletableFuture<Void> preloadRange(@NonNull String filePath, long startFrame, long endFrame) {
        if (isShutdown.get()) {
            log.debug("PreloadManager is shutdown, skipping preload");
            return CompletableFuture.completedFuture(null);
        }

        PreloadKey key = new PreloadKey(filePath, startFrame, endFrame);

        // Check if already cached
        if (cache.getIfPresent(key) != null) {
            log.debug("Segment already cached: {} frames {}-{}", filePath, startFrame, endFrame);
            return CompletableFuture.completedFuture(null);
        }

        // Preload asynchronously - failures are logged but not propagated
        return CompletableFuture.runAsync(
                () -> {
                    // Create a new sound object with entire file loaded into memory
                    PointerByReference soundRef = new PointerByReference();
                    int mode = FmodConstants.FMOD_CREATESAMPLE | FmodConstants.FMOD_ACCURATETIME;

                    int result =
                            fmod.FMOD_System_CreateSound(system, filePath, mode, null, soundRef);

                    if (result != FmodConstants.FMOD_OK) {
                        // Silent failure - preloading is an optimization
                        log.warn(
                                "Failed to preload segment: {} frames {}-{}: FMOD error {}",
                                filePath,
                                startFrame,
                                endFrame,
                                result);
                        return;
                    }

                    Pointer sound = soundRef.getValue();

                    // Get sound size for memory tracking
                    IntByReference lengthRef = new IntByReference();
                    result =
                            fmod.FMOD_Sound_GetLength(
                                    sound, lengthRef, FmodConstants.FMOD_TIMEUNIT_PCMBYTES);

                    long soundBytes = 0;
                    if (result == FmodConstants.FMOD_OK) {
                        soundBytes = lengthRef.getValue();
                        soundSizes.put(sound, soundBytes);
                        totalMemoryBytes.addAndGet(soundBytes);
                    }

                    // Store in cache (will auto-evict LRU if at capacity)
                    cache.put(key, sound);

                    log.debug(
                            "Preloaded segment: {} frames {}-{} ({} bytes)",
                            filePath,
                            startFrame,
                            endFrame,
                            soundBytes);
                },
                asyncExecutor);
    }

    /**
     * Get a preloaded sound if available in cache.
     *
     * @param filePath The audio file path
     * @param startFrame Starting frame (inclusive)
     * @param endFrame Ending frame (exclusive)
     * @return The preloaded FMOD sound pointer, or empty if not cached
     */
    Optional<Pointer> getPreloadedSound(@NonNull String filePath, long startFrame, long endFrame) {
        if (isShutdown.get()) {
            return Optional.empty();
        }

        PreloadKey key = new PreloadKey(filePath, startFrame, endFrame);
        Pointer sound = cache.getIfPresent(key);

        if (sound != null) {
            log.trace("Cache hit for segment: {} frames {}-{}", filePath, startFrame, endFrame);
        }

        return Optional.ofNullable(sound);
    }

    /**
     * Check if a specific range is currently cached.
     *
     * @param filePath The audio file path
     * @param startFrame Starting frame (inclusive)
     * @param endFrame Ending frame (exclusive)
     * @return true if the range is cached and ready for instant playback
     */
    boolean isRangeCached(@NonNull String filePath, long startFrame, long endFrame) {
        if (isShutdown.get()) {
            return false;
        }

        PreloadKey key = new PreloadKey(filePath, startFrame, endFrame);
        return cache.getIfPresent(key) != null;
    }

    /**
     * Clear all cached segments for a specific file. Useful when switching to a different audio
     * file.
     *
     * @param filePath The audio file path to clear from cache
     */
    void clearFile(@NonNull String filePath) {
        if (isShutdown.get()) {
            return;
        }

        // Find and invalidate all keys for this file
        cache.asMap().keySet().stream()
                .filter(key -> key.filePath.equals(filePath))
                .forEach(cache::invalidate);

        log.debug("Cleared all cached segments for file: {}", filePath);
    }

    /**
     * Clear the entire cache, releasing all preloaded sounds. Called during shutdown or when memory
     * needs to be freed.
     */
    void clearCache() {
        if (isShutdown.get()) {
            return;
        }

        cache.invalidateAll();
        log.debug("Cleared entire preload cache");
    }

    /**
     * Get current cache statistics.
     *
     * @return Cache statistics including size, hit rate, etc.
     */
    CacheStats getCacheStats() {
        return new CacheStats((int) cache.estimatedSize(), maxCacheSize, totalMemoryBytes.get());
    }

    /**
     * Shutdown the preload manager and release all resources. After calling this, the manager
     * cannot be used again.
     */
    void shutdown() {
        if (isShutdown.compareAndSet(false, true)) {
            log.info("Shutting down FmodPreloadManager");

            // Clear cache (will trigger cleanup via removal listener)
            cache.invalidateAll();
            cache.cleanUp();

            // Clear tracking maps
            soundSizes.clear();
            totalMemoryBytes.set(0);

            log.info("FmodPreloadManager shutdown complete");
        }
    }

    /** Release an FMOD sound and update memory tracking. */
    private void releaseSound(Pointer sound, PreloadKey key) {
        try {
            // Update memory tracking
            Long soundBytes = soundSizes.remove(sound);
            if (soundBytes != null) {
                totalMemoryBytes.addAndGet(-soundBytes);
            }

            // Release FMOD sound
            int result = fmod.FMOD_Sound_Release(sound);
            if (result != FmodConstants.FMOD_OK) {
                log.warn(
                        "Failed to release preloaded sound for {} frames {}-{}: FMOD error {}",
                        key.filePath,
                        key.startFrame,
                        key.endFrame,
                        result);
            } else {
                log.trace(
                        "Released preloaded sound for {} frames {}-{}",
                        key.filePath,
                        key.startFrame,
                        key.endFrame);
            }
        } catch (Exception e) {
            log.warn("Error releasing preloaded sound", e);
        }
    }

    /** Statistics about cache usage. */
    record CacheStats(int currentSize, int maxSize, long estimatedMemoryBytes) {

        public boolean isFull() {
            return currentSize >= maxSize;
        }

        public double getUtilization() {
            return maxSize == 0 ? 0.0 : (double) currentSize / maxSize;
        }
    }

    /** Key for identifying cached audio segments. Package-private for testing. */
    record PreloadKey(@NonNull String filePath, long startFrame, long endFrame) {

        PreloadKey {
            if (startFrame < 0) {
                throw new IllegalArgumentException("Start frame must be non-negative");
            }
            if (endFrame <= startFrame) {
                throw new IllegalArgumentException("End frame must be greater than start frame");
            }
        }
    }
}
