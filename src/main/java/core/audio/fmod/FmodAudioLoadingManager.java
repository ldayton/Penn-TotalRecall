package core.audio.fmod;

import com.google.errorprone.annotations.ThreadSafe;
import core.audio.AudioHandle;
import core.audio.AudioMetadata;
import core.audio.exceptions.AudioEngineException;
import core.audio.exceptions.AudioLoadException;
import core.audio.fmod.panama.FmodCore;
import java.io.File;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages audio file loading and lifecycle for the FMOD audio engine. Maintains the single-audio
 * paradigm where only one audio file is "current" at a time.
 */
@ThreadSafe
@Slf4j
class FmodAudioLoadingManager {

    // Immutable record to hold current audio state atomically
    private record CurrentAudio(
            @NonNull FmodAudioHandle handle, @NonNull MemorySegment sound, @NonNull String path) {}

    private final MemorySegment system;
    private final FmodSystemStateManager stateManager;
    private final FmodHandleLifecycleManager lifecycleManager;
    private final ReentrantLock loadingLock = new ReentrantLock();

    // Current loaded audio (single-audio paradigm) - guarded by loadingLock
    private volatile Optional<CurrentAudio> current = Optional.empty();

    FmodAudioLoadingManager(
            @NonNull MemorySegment system,
            @NonNull FmodSystemStateManager stateManager,
            @NonNull FmodHandleLifecycleManager lifecycleManager) {
        this.system = system;
        this.stateManager = stateManager;
        this.lifecycleManager = lifecycleManager;
    }

    /**
     * Load an audio file. Returns the same handle if the file is already loaded. This method
     * acquires the loadingLock for thread-safe operations.
     *
     * @param filePath Path to the audio file
     * @return Handle to the loaded audio
     * @throws AudioLoadException if the file cannot be loaded
     */
    AudioHandle loadAudio(@NonNull String filePath) throws AudioLoadException {
        loadingLock.lock();
        try {
            // Validate and normalize the path
            String canonicalPath = validateAndNormalize(filePath);

            // Check if this file is already loaded
            Optional<CurrentAudio> existing = current;
            if (existing.isPresent() && existing.get().path().equals(canonicalPath)) {
                // Same file already loaded, return existing handle
                return existing.get().handle();
            }

            // Create new sound BEFORE releasing old one (to ensure we always have valid audio)
            MemorySegment newSound;
            try {
                newSound = createSound(canonicalPath);
            } catch (AudioLoadException e) {
                // Failed to create new sound - keep the old one
                throw e;
            }

            // Only release previous audio after successfully creating new one
            if (existing.isPresent()) {
                int result = FmodCore.FMOD_Sound_Release(existing.get().sound());
                if (result != FmodConstants.FMOD_OK
                        && result != FmodConstants.FMOD_ERR_INVALID_HANDLE) {
                    log.warn(
                            "Error releasing previous sound '{}': error code {}",
                            existing.get().path(),
                            result);
                }
            }

            // Create handle for the new audio using the lifecycle manager
            FmodAudioHandle newHandle = lifecycleManager.createHandle(newSound, canonicalPath);

            // Update current state atomically
            current = Optional.of(new CurrentAudio(newHandle, newSound, canonicalPath));

            return newHandle;

        } finally {
            loadingLock.unlock();
        }
    }

    /**
     * Get metadata for the currently loaded core.audio. This method acquires the loadingLock for
     * thread-safe operations.
     *
     * @return Metadata if audio is loaded, empty otherwise
     */
    Optional<AudioMetadata> getCurrentMetadata() {
        loadingLock.lock();
        try {
            return current.map(
                    audio -> {
                        try {
                            return extractMetadata(audio.sound());
                        } catch (AudioLoadException e) {
                            log.warn(
                                    "Failed to extract metadata for '{}': {}",
                                    audio.path(),
                                    e.getMessage());
                            // Return basic metadata with what we know
                            return new AudioMetadata(
                                    48000, // Default sample rate
                                    2, // Default stereo
                                    16, // Default bit depth
                                    "Unknown", 0L, // Unknown frame count
                                    0.0); // Unknown duration
                        }
                    });
        } finally {
            loadingLock.unlock();
        }
    }

    /**
     * Check if the given handle represents the current core.audio.
     *
     * @param handle The handle to check
     * @return true if this is the current audio
     */
    boolean isCurrent(@NonNull AudioHandle handle) {
        return lifecycleManager.isCurrent(handle);
    }

    /**
     * Get the current FMOD sound pointer.
     *
     * @return Current sound if loaded, empty otherwise
     */
    Optional<MemorySegment> getCurrentSound() {
        return current.map(CurrentAudio::sound);
    }

    /**
     * Get the current audio handle.
     *
     * @return Current handle if loaded, empty otherwise
     */
    Optional<FmodAudioHandle> getCurrentHandle() {
        return current.map(CurrentAudio::handle);
    }

    /**
     * Release all loaded audio resources. This method acquires the loadingLock for thread-safe
     * operations.
     */
    void releaseAll() {
        loadingLock.lock();
        try {
            current.ifPresent(
                    audio -> {
                        int result = FmodCore.FMOD_Sound_Release(audio.sound());
                        if (result != FmodConstants.FMOD_OK
                                && result != FmodConstants.FMOD_ERR_INVALID_HANDLE) {
                            log.warn(
                                    "Error releasing sound for '{}': error code {}",
                                    audio.path(),
                                    result);
                        }
                    });
            // Always clear reference even if release failed to prevent use-after-free
            current = Optional.empty();
            // Clear the lifecycle manager's current handle
            lifecycleManager.clear();
        } finally {
            loadingLock.unlock();
        }
    }

    private String validateAndNormalize(@NonNull String filePath) throws AudioLoadException {
        File file = new File(filePath);

        // Check file exists
        if (!file.exists()) {
            throw new AudioLoadException("Audio file not found: " + filePath);
        }

        // Check file is readable
        if (!file.canRead()) {
            throw new AudioLoadException("Cannot read audio file: " + filePath);
        }

        // Check it's a file, not a directory
        if (file.isDirectory()) {
            throw new AudioLoadException("Path is a directory, not a file: " + filePath);
        }

        // Get canonical path to normalize different representations of the same file
        try {
            return file.getCanonicalPath();
        } catch (IOException e) {
            throw new AudioLoadException("Failed to resolve file path: " + filePath, e);
        }
    }

    /**
     * Create an FMOD sound from a file. This method must be called while holding the loadingLock.
     */
    private MemorySegment createSound(@NonNull String canonicalPath) throws AudioLoadException {
        // Check we're in the right state
        try {
            stateManager.checkState(FmodSystemStateManager.State.INITIALIZED);
        } catch (AudioEngineException e) {
            throw new AudioLoadException("Audio engine not initialized");
        }

        // Set appropriate flags for playback
        int flags = FmodConstants.FMOD_DEFAULT | FmodConstants.FMOD_ACCURATETIME;

        // Create the sound
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment soundRef = arena.allocate(ValueLayout.ADDRESS);
            MemorySegment path = arena.allocateFrom(canonicalPath);
            int result =
                    FmodCore.FMOD_System_CreateSound(
                            system, path, flags, MemorySegment.NULL, soundRef);

            if (result != FmodConstants.FMOD_OK) {
                throw FmodError.toLoadException(result, canonicalPath);
            }

            MemorySegment sound = soundRef.get(ValueLayout.ADDRESS, 0);
            if (sound == null) {
                throw new AudioLoadException("FMOD returned null sound for: " + canonicalPath);
            }

            return sound;
        }
    }

    // Error mapping centralized in FmodError

    /**
     * Extract metadata from an FMOD sound. This method must be called while holding the
     * loadingLock.
     */
    private AudioMetadata extractMetadata(@NonNull MemorySegment sound) throws AudioLoadException {
        // Get sound format info
        try (Arena arena = Arena.ofConfined()) {
            var typeRef = arena.allocate(ValueLayout.JAVA_INT); // File type (WAV, MP3, etc.)
            var formatRef = arena.allocate(ValueLayout.JAVA_INT); // Sample format (not used)
            var channelsRef = arena.allocate(ValueLayout.JAVA_INT); // Number of channels
            var bitsRef = arena.allocate(ValueLayout.JAVA_INT); // Bits per sample

            int result =
                    FmodCore.FMOD_Sound_GetFormat(sound, typeRef, formatRef, channelsRef, bitsRef);

            if (result != FmodConstants.FMOD_OK) {
                throw new AudioLoadException(
                        "Failed to extract audio format metadata (error code: " + result + ")");
            }

            // Get length in milliseconds
            var lengthMsRef = arena.allocate(ValueLayout.JAVA_INT);
            result =
                    FmodCore.FMOD_Sound_GetLength(
                            sound, lengthMsRef, FmodConstants.FMOD_TIMEUNIT_MS);

            if (result != FmodConstants.FMOD_OK) {
                throw new AudioLoadException(
                        "Failed to get audio duration (error code: " + result + ")");
            }

            // Get the actual sample rate from the sound
            var frequencyRef = arena.allocate(ValueLayout.JAVA_FLOAT);
            var priorityRef = arena.allocate(ValueLayout.JAVA_INT); // Not used
            result = FmodCore.FMOD_Sound_GetDefaults(sound, frequencyRef, priorityRef);

            if (result != FmodConstants.FMOD_OK) {
                throw new AudioLoadException(
                        "Failed to get sample rate (error code: " + result + ")");
            }

            // Get total samples for precise duration
            var lengthSamplesRef = arena.allocate(ValueLayout.JAVA_INT);
            result =
                    FmodCore.FMOD_Sound_GetLength(
                            sound, lengthSamplesRef, FmodConstants.FMOD_TIMEUNIT_PCM);

            if (result != FmodConstants.FMOD_OK) {
                throw new AudioLoadException(
                        "Failed to get total samples (error code: " + result + ")");
            }

            long totalSamples =
                    Integer.toUnsignedLong(lengthSamplesRef.get(ValueLayout.JAVA_INT, 0));
            int sampleRate = Math.round(frequencyRef.get(ValueLayout.JAVA_FLOAT, 0));

            // Map sound type to format string
            String format = mapSoundTypeToFormat(typeRef.get(ValueLayout.JAVA_INT, 0));

            // Calculate precise duration from samples
            double durationSeconds = totalSamples / (double) sampleRate;

            return new AudioMetadata(
                    sampleRate,
                    channelsRef.get(ValueLayout.JAVA_INT, 0),
                    bitsRef.get(ValueLayout.JAVA_INT, 0),
                    format,
                    totalSamples,
                    durationSeconds);
        }
    }

    /** Map FMOD sound type to human-readable format string. */
    private String mapSoundTypeToFormat(int soundType) {
        return switch (soundType) {
            case FmodConstants.FMOD_SOUND_TYPE_WAV -> "WAV";
            case FmodConstants.FMOD_SOUND_TYPE_AIFF -> "AIFF";
            case FmodConstants.FMOD_SOUND_TYPE_MPEG -> "MP3";
            case FmodConstants.FMOD_SOUND_TYPE_OGGVORBIS -> "OGG";
            case FmodConstants.FMOD_SOUND_TYPE_FLAC -> "FLAC";
            case FmodConstants.FMOD_SOUND_TYPE_OPUS -> "Opus";
            case FmodConstants.FMOD_SOUND_TYPE_RAW -> "RAW";
            default -> "Unknown";
        };
    }
}
