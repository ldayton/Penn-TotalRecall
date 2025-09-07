package core.audio.fmod;

import app.annotations.ThreadSafe;
import com.google.inject.Singleton;
import com.sun.jna.Pointer;
import core.audio.AudioHandle;
import java.util.concurrent.atomic.AtomicLong;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages the lifecycle and validity of audio handles using a generation-based system.
 *
 * <p>This class ensures that only handles from the current generation are valid, providing atomic
 * invalidation of all previous handles when a new audio file is loaded.
 *
 * <p>Thread-safe: All operations are atomic and thread-safe.
 */
@ThreadSafe
@Singleton
@Slf4j
class FmodHandleLifecycleManager {

    private final AtomicLong currentGeneration = new AtomicLong(0);
    private final AtomicLong nextHandleId = new AtomicLong(1);

    // The current valid handle - only one at a time
    private volatile FmodAudioHandle currentHandle = null;

    /**
     * Creates a new audio handle for the given audio resource. This atomically invalidates any
     * previously created handles by incrementing the generation.
     *
     * @param sound The FMOD sound pointer
     * @param filePath The path to the audio file
     * @return A new valid audio handle
     */
    @NonNull
    FmodAudioHandle createHandle(@NonNull Pointer sound, @NonNull String filePath) {
        // Increment generation to invalidate all previous handles
        long generation = currentGeneration.incrementAndGet();
        long id = nextHandleId.getAndIncrement();

        // Create the new handle with the current generation
        FmodAudioHandle handle = new FmodAudioHandle(id, generation, sound, filePath, this);

        // Atomically update the current handle
        FmodAudioHandle previousHandle = currentHandle;
        currentHandle = handle;

        if (previousHandle != null) {}

        return handle;
    }

    /**
     * Checks if the given handle is valid (from the current generation and is the current handle).
     *
     * @param handle The handle to check
     * @return true if the handle is valid, false otherwise
     */
    boolean isValid(AudioHandle handle) {
        if (!(handle instanceof FmodAudioHandle)) {
            return false;
        }

        FmodAudioHandle fmodHandle = (FmodAudioHandle) handle;

        // A handle is valid if:
        // 1. It's not null
        // 2. It matches the current generation
        // 3. It is the current handle (object identity)
        return fmodHandle != null
                && fmodHandle.getGeneration() == currentGeneration.get()
                && fmodHandle == currentHandle;
    }

    /**
     * Gets the current valid handle, if any.
     *
     * @return The current handle or null if no audio is loaded
     */
    FmodAudioHandle getCurrentHandle() {
        return currentHandle;
    }

    /**
     * Gets the current generation number.
     *
     * @return The current generation
     */
    long getCurrentGeneration() {
        return currentGeneration.get();
    }

    /**
     * Checks if the given handle is the current handle.
     *
     * @param handle The handle to check
     * @return true if this is the current handle
     */
    boolean isCurrent(@NonNull AudioHandle handle) {
        return handle == currentHandle;
    }

    /** Clears the current handle (used when closing the engine). */
    void clear() {
        currentHandle = null;
    }
}
