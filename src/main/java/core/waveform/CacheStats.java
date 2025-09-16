package core.waveform;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import core.dispatch.EventDispatchBus;
import core.dispatch.Subscribe;
import core.events.AppStateChangedEvent;
import java.util.concurrent.atomic.AtomicLong;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/** Thread-safe cache statistics tracker for waveform segment cache. */
@Slf4j
@Singleton
public class CacheStats {
    private final AtomicLong requests = new AtomicLong();
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong puts = new AtomicLong();
    private final AtomicLong updates = new AtomicLong();
    private final AtomicLong evictions = new AtomicLong();
    private final AtomicLong clears = new AtomicLong();
    private final AtomicLong resizes = new AtomicLong();

    @Inject
    public CacheStats(@NonNull EventDispatchBus eventBus) {
        eventBus.subscribe(this);
    }

    @Subscribe
    public void onStateChanged(@NonNull AppStateChangedEvent event) {
        switch (event.newState()) {
            case NO_AUDIO, LOADING -> {
                log.debug("Resetting cache stats on state change to: {}", event.newState());
                reset();
            }
            default -> {}
        }
    }

    void recordRequest() {
        requests.incrementAndGet();
    }

    void recordHit() {
        hits.incrementAndGet();
    }

    void recordMiss() {
        misses.incrementAndGet();
    }

    void recordPut() {
        puts.incrementAndGet();
    }

    void recordUpdate() {
        updates.incrementAndGet();
    }

    void recordEviction() {
        evictions.incrementAndGet();
    }

    void recordClear(int itemsCleared) {
        clears.incrementAndGet();
        evictions.addAndGet(itemsCleared);
    }

    void recordResize(int oldSize, int newSize) {
        resizes.incrementAndGet();
        log.debug("Cache resized from {} to {} entries", oldSize, newSize);
    }

    public long getRequests() {
        return requests.get();
    }

    public long getHits() {
        return hits.get();
    }

    public long getMisses() {
        return misses.get();
    }

    public double getHitRate() {
        long totalRequests = requests.get();
        return totalRequests > 0 ? (double) hits.get() / totalRequests : 0.0;
    }

    public long getPuts() {
        return puts.get();
    }

    public long getUpdates() {
        return updates.get();
    }

    public long getEvictions() {
        return evictions.get();
    }

    public long getClears() {
        return clears.get();
    }

    public long getResizes() {
        return resizes.get();
    }

    public void logStats() {
        log.info(
                "Cache stats: {} requests, {} hits ({:.1f}% hit rate), {} misses, {} puts, {}"
                        + " evictions",
                requests.get(),
                hits.get(),
                getHitRate() * 100,
                misses.get(),
                puts.get(),
                evictions.get());
    }

    public void reset() {
        requests.set(0);
        hits.set(0);
        misses.set(0);
        puts.set(0);
        updates.set(0);
        evictions.set(0);
        clears.set(0);
        resizes.set(0);
    }

    @Override
    public String toString() {
        long totalRequests = requests.get();
        long totalHits = hits.get();
        long totalMisses = misses.get();
        double hitRate = getHitRate();

        return String.format(
                "CacheStats[requests=%d, hits=%d (%.1f%%), misses=%d, puts=%d, updates=%d,"
                        + " evictions=%d, clears=%d, resizes=%d]",
                totalRequests,
                totalHits,
                hitRate * 100,
                totalMisses,
                puts.get(),
                updates.get(),
                evictions.get(),
                clears.get(),
                resizes.get());
    }
}
