package a2;

import lombok.NonNull;

/**
 * Factory for creating SampleReader instances with different configurations.
 *
 * <p>This factory provides different strategies for creating readers based on the use case:
 *
 * <ul>
 *   <li>Single-threaded reading: {@link #createReader()}
 *   <li>Parallel reading with thread pool: {@link #createPooledReader(int)}
 *   <li>Per-thread instances: {@link #createThreadLocalReader()}
 * </ul>
 */
public interface SampleReaderFactory {

    /**
     * Creates a basic sample reader suitable for single-threaded use.
     *
     * <p>The returned reader is thread-safe but may not be optimized for high-throughput parallel
     * reads.
     *
     * @return A new sample reader instance
     */
    @NonNull
    SampleReader createReader();

    /**
     * Creates a sample reader with a dedicated thread pool for parallel reads.
     *
     * <p>This reader is optimized for parallel reading of multiple segments from the same or
     * different files. The thread pool size determines the maximum parallelism.
     *
     * @param parallelism Size of the thread pool (typically number of CPU cores)
     * @return A pooled sample reader
     * @throws IllegalArgumentException if parallelism is less than 1
     */
    @NonNull
    SampleReader createPooledReader(int parallelism);

    /**
     * Creates a thread-local sample reader.
     *
     * <p>Each thread gets its own reader instance, avoiding any contention. This is useful when
     * readers maintain state or resources that shouldn't be shared between threads.
     *
     * <p>The returned reader should only be used by the creating thread.
     *
     * @return A new reader for exclusive use by the current thread
     */
    @NonNull
    default SampleReader createThreadLocalReader() {
        // Default implementation just creates a regular reader
        // Implementations can override for thread-specific optimizations
        return createReader();
    }

    /**
     * Gets the default factory implementation.
     *
     * <p>Returns an FMOD-based implementation if available, otherwise falls back to Java's built-in
     * audio support.
     *
     * @return The default factory instance
     */
    @NonNull
    static SampleReaderFactory getDefault() {
        // TODO: Implement FmodSampleReaderFactory
        throw new UnsupportedOperationException("Default factory not yet implemented");
    }
}
