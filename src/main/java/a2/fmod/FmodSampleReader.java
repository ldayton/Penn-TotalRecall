package a2.fmod;

import a2.*;
import a2.exceptions.AudioEngineException;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.*;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Simple FMOD-based SampleReader that loads entire files into memory.
 *
 * <p>Since we use FMOD_CREATESAMPLE which loads the entire file into memory, we don't need complex
 * parallel infrastructure. We just cache the loaded audio data and serve reads directly from
 * memory.
 */
@Slf4j
public class FmodSampleReader implements SampleReader {

    private final FmodLibrary fmod;
    private final Pointer system;
    private final Map<Path, CachedAudio> cache = new ConcurrentHashMap<>();
    private volatile boolean closed = false;

    private static class CachedAudio {
        final double[] samples;
        final AudioMetadata metadata;

        CachedAudio(double[] samples, AudioMetadata metadata) {
            this.samples = samples;
            this.metadata = metadata;
        }
    }

    public FmodSampleReader(@NonNull FmodLibraryLoader libraryLoader) {
        try {
            // Load FMOD library
            this.fmod = libraryLoader.loadAudioLibrary(FmodLibrary.class);

            // Create single FMOD system
            PointerByReference systemRef = new PointerByReference();
            int result = fmod.FMOD_System_Create(systemRef, FmodConstants.FMOD_VERSION);
            if (result != FmodConstants.FMOD_OK) {
                throw new AudioEngineException(
                        "Failed to create FMOD system: " + FmodError.describe(result));
            }
            this.system = systemRef.getValue();

            // Initialize with minimal settings since we're just loading files
            result = fmod.FMOD_System_Init(system, 32, FmodConstants.FMOD_INIT_NORMAL, null);
            if (result != FmodConstants.FMOD_OK) {
                fmod.FMOD_System_Release(system);
                throw new AudioEngineException(
                        "Failed to initialize FMOD system: " + FmodError.describe(result));
            }

            log.info("Created simple FMOD sample reader");
        } catch (AudioEngineException e) {
            throw new RuntimeException("Failed to initialize FMOD system", e);
        }
    }

    @Override
    public CompletableFuture<AudioData> readSamples(
            @NonNull Path audioFile, long startFrame, long frameCount) {

        if (closed) {
            return CompletableFuture.failedFuture(
                    new AudioReadException("Reader is closed", audioFile));
        }

        if (startFrame < 0 || frameCount < 0) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Negative frame values not allowed"));
        }

        // Load file if not cached, then read from memory
        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        CachedAudio cached = loadOrGetCached(audioFile);
                        return readFromCache(cached, startFrame, frameCount);
                    } catch (AudioReadException e) {
                        throw new CompletionException(e);
                    }
                });
    }

    @Override
    public CompletableFuture<AudioMetadata> getMetadata(@NonNull Path audioFile) {
        if (closed) {
            return CompletableFuture.failedFuture(
                    new AudioReadException("Reader is closed", audioFile));
        }

        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        CachedAudio cached = loadOrGetCached(audioFile);
                        return cached.metadata;
                    } catch (AudioReadException e) {
                        throw new CompletionException(e);
                    }
                });
    }

    private synchronized CachedAudio loadOrGetCached(Path audioFile) throws AudioReadException {
        // Check cache first
        CachedAudio cached = cache.get(audioFile);
        if (cached != null) {
            return cached;
        }

        // Load the entire file into memory
        String filePath = audioFile.toAbsolutePath().toString();
        Pointer sound = null;

        try {
            // Update system
            fmod.FMOD_System_Update(system);

            // Create sound as sample (loads entire file into memory)
            // Don't use FMOD_OPENONLY - that prevents actual data loading!
            PointerByReference soundRef = new PointerByReference();
            int flags = FmodConstants.FMOD_CREATESAMPLE;

            int result = fmod.FMOD_System_CreateSound(system, filePath, flags, null, soundRef);
            if (result != FmodConstants.FMOD_OK) {
                throw new AudioReadException(
                        "Failed to open audio file: " + FmodError.describe(result), audioFile);
            }
            sound = soundRef.getValue();

            // Get format info
            IntByReference channelsRef = new IntByReference();
            IntByReference bitsRef = new IntByReference();
            result = fmod.FMOD_Sound_GetFormat(sound, null, null, channelsRef, bitsRef);
            if (result != FmodConstants.FMOD_OK) {
                throw new AudioReadException(
                        "Failed to get sound format: " + FmodError.describe(result), audioFile);
            }

            // Get sample rate
            var frequencyRef = new com.sun.jna.ptr.FloatByReference();
            result = fmod.FMOD_Sound_GetDefaults(sound, frequencyRef, null);
            if (result != FmodConstants.FMOD_OK) {
                throw new AudioReadException(
                        "Failed to get sample rate: " + FmodError.describe(result), audioFile);
            }

            // Get total length
            IntByReference lengthRef = new IntByReference();
            result = fmod.FMOD_Sound_GetLength(sound, lengthRef, FmodConstants.FMOD_TIMEUNIT_PCM);
            if (result != FmodConstants.FMOD_OK) {
                throw new AudioReadException(
                        "Failed to get sound length: " + FmodError.describe(result), audioFile);
            }

            int sampleRate = Math.round(frequencyRef.getValue());
            int channelCount = channelsRef.getValue();
            int bitsPerSample = bitsRef.getValue();
            int bytesPerSample = bitsPerSample / 8;
            long totalFrames = lengthRef.getValue();

            // Get duration in ms
            IntByReference msLengthRef = new IntByReference();
            result = fmod.FMOD_Sound_GetLength(sound, msLengthRef, FmodConstants.FMOD_TIMEUNIT_MS);
            if (result != FmodConstants.FMOD_OK) {
                throw new AudioReadException(
                        "Failed to get duration: " + FmodError.describe(result), audioFile);
            }

            // Lock entire sound to read all data at once
            PointerByReference ptr1Ref = new PointerByReference();
            PointerByReference ptr2Ref = new PointerByReference();
            IntByReference len1Ref = new IntByReference();
            IntByReference len2Ref = new IntByReference();

            int totalBytes = (int) (totalFrames * channelCount * bytesPerSample);

            result = fmod.FMOD_Sound_Lock(sound, 0, totalBytes, ptr1Ref, ptr2Ref, len1Ref, len2Ref);
            if (result != FmodConstants.FMOD_OK) {
                throw new AudioReadException(
                        "Failed to lock sound data: " + FmodError.describe(result), audioFile);
            }

            try {
                // Read all PCM data
                int totalBytesRead = len1Ref.getValue() + len2Ref.getValue();
                byte[] buffer = new byte[totalBytesRead];

                if (len1Ref.getValue() > 0 && ptr1Ref.getValue() != null) {
                    ptr1Ref.getValue().read(0, buffer, 0, len1Ref.getValue());
                }

                if (len2Ref.getValue() > 0 && ptr2Ref.getValue() != null) {
                    ptr2Ref.getValue().read(0, buffer, len1Ref.getValue(), len2Ref.getValue());
                }

                // Debug: Check raw byte data
                boolean hasNonZero = false;
                for (int i = 0; i < Math.min(1000, buffer.length); i++) {
                    if (buffer[i] != 0) {
                        hasNonZero = true;
                        break;
                    }
                }
                log.debug(
                        "Raw PCM bytes: {} total, has non-zero data: {}",
                        totalBytesRead,
                        hasNonZero);

                // Convert to normalized doubles
                int totalSamples = totalBytesRead / bytesPerSample;
                double[] samples = new double[totalSamples];
                convertToDouble(buffer, samples, bitsPerSample, totalSamples);

                // Create metadata
                String formatStr =
                        String.format(
                                "%d Hz, %d bit, %s",
                                sampleRate, bitsPerSample, channelCount == 1 ? "Mono" : "Stereo");

                AudioMetadata metadata =
                        new AudioMetadata(
                                sampleRate,
                                channelCount,
                                bitsPerSample,
                                formatStr,
                                totalFrames,
                                msLengthRef.getValue() / 1000.0);

                // Debug: Check if samples have actual data
                double maxSample = 0;
                for (double s : samples) {
                    if (Math.abs(s) > maxSample) maxSample = Math.abs(s);
                }

                // Cache and return
                cached = new CachedAudio(samples, metadata);
                cache.put(audioFile, cached);

                log.debug(
                        "Loaded and cached {} ({} frames, {} MB, max amplitude: {})",
                        audioFile.getFileName(),
                        totalFrames,
                        String.format("%.2f", totalBytesRead / 1_000_000.0),
                        maxSample);

                return cached;

            } finally {
                // Unlock the sound
                fmod.FMOD_Sound_Unlock(
                        sound,
                        ptr1Ref.getValue(),
                        ptr2Ref.getValue(),
                        len1Ref.getValue(),
                        len2Ref.getValue());
            }

        } finally {
            // Release the sound object
            if (sound != null) {
                fmod.FMOD_Sound_Release(sound);
            }
        }
    }

    private AudioData readFromCache(CachedAudio cached, long startFrame, long frameCount) {
        AudioMetadata meta = cached.metadata;
        int channelCount = meta.channelCount();
        long totalFrames = meta.frameCount();

        // Validate range
        if (startFrame >= totalFrames) {
            return AudioData.empty(meta.sampleRate(), channelCount, startFrame);
        }

        long actualFrameCount = Math.min(frameCount, totalFrames - startFrame);
        if (actualFrameCount <= 0) {
            return AudioData.empty(meta.sampleRate(), channelCount, startFrame);
        }

        // Calculate sample positions
        int startSample = (int) (startFrame * channelCount);
        int sampleCount = (int) (actualFrameCount * channelCount);

        // Copy the requested range from cached samples
        double[] resultSamples = new double[sampleCount];
        System.arraycopy(cached.samples, startSample, resultSamples, 0, sampleCount);

        return new AudioData(
                resultSamples, meta.sampleRate(), channelCount, startFrame, actualFrameCount);
    }

    private void convertToDouble(
            byte[] buffer, double[] samples, int bitsPerSample, int numSamples) {
        if (bitsPerSample == 16) {
            for (int i = 0; i < numSamples; i++) {
                int byteIndex = i * 2;
                short value = (short) ((buffer[byteIndex] & 0xFF) | (buffer[byteIndex + 1] << 8));
                samples[i] = value / 32768.0;
            }
        } else if (bitsPerSample == 24) {
            for (int i = 0; i < numSamples; i++) {
                int byteIndex = i * 3;
                int value =
                        (buffer[byteIndex] & 0xFF)
                                | ((buffer[byteIndex + 1] & 0xFF) << 8)
                                | (buffer[byteIndex + 2] << 16);
                if ((value & 0x800000) != 0) {
                    value |= 0xFF000000;
                }
                samples[i] = value / 8388608.0;
            }
        } else if (bitsPerSample == 32) {
            for (int i = 0; i < numSamples; i++) {
                int byteIndex = i * 4;
                int value =
                        (buffer[byteIndex] & 0xFF)
                                | ((buffer[byteIndex + 1] & 0xFF) << 8)
                                | ((buffer[byteIndex + 2] & 0xFF) << 16)
                                | (buffer[byteIndex + 3] << 24);
                samples[i] = value / 2147483648.0;
            }
        } else {
            throw new UnsupportedOperationException("Unsupported bit depth: " + bitsPerSample);
        }
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }

        closed = true;

        // Clear cache
        cache.clear();

        // Release FMOD system
        if (system != null) {
            fmod.FMOD_System_Release(system);
            log.info("Released FMOD system");
        }
    }
}
