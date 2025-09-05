package a2.fmod;

import a2.SampleReader;
import a2.SampleReaderFactory;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.NonNull;

/**
 * Factory for creating FMOD-based sample readers with different configurations.
 *
 * <p>This factory provides optimized readers for different use cases:
 *
 * <ul>
 *   <li>Single-threaded: Basic reader with one FMOD system
 *   <li>Pooled parallel: Reader with configurable thread pool for parallel reads
 *   <li>Thread-local: Each thread gets its own reader instance
 * </ul>
 */
@Singleton
public class FmodSampleReaderFactory implements SampleReaderFactory {

    private final FmodLibraryLoader libraryLoader;

    @Inject
    public FmodSampleReaderFactory(@NonNull FmodLibraryLoader libraryLoader) {
        this.libraryLoader = libraryLoader;
    }

    @Override
    @NonNull
    public SampleReader createReader() {
        // Create a basic reader with single thread
        return new FmodParallelSampleReader(libraryLoader, 1);
    }

    @Override
    @NonNull
    public SampleReader createPooledReader(int parallelism) {
        if (parallelism < 1) {
            throw new IllegalArgumentException("Parallelism must be at least 1: " + parallelism);
        }
        return new FmodParallelSampleReader(libraryLoader, parallelism);
    }

    @Override
    @NonNull
    public SampleReader createThreadLocalReader() {
        // For thread-local, we create a reader with parallelism matching CPU cores
        // This gives good performance for typical use cases
        return new FmodParallelSampleReader(
                libraryLoader, Runtime.getRuntime().availableProcessors());
    }
}
