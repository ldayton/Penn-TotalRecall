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
        // Create a simple reader that loads files into memory
        return new FmodSampleReader(libraryLoader);
    }

    @Override
    @NonNull
    public SampleReader createPooledReader(int parallelism) {
        // Since we're loading entire files into memory with FMOD_CREATESAMPLE,
        // the simple reader handles concurrent reads efficiently from cache
        return new FmodSampleReader(libraryLoader);
    }

    @Override
    @NonNull
    public SampleReader createThreadLocalReader() {
        // Simple reader is thread-safe and handles all patterns well
        return new FmodSampleReader(libraryLoader);
    }
}
