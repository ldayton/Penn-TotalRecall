package a2.fmod;

import a2.*;
import a2.exceptions.AudioEngineException;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.*;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * FMOD-based implementation of SampleReader optimized for parallel reads.
 *
 * <p>Uses a single shared FMOD system with multiple channels to enable true parallel reading. Each
 * read creates its own Sound object with FMOD_CREATECOMPRESSEDSAMPLE flag which allows multiple
 * simultaneous playbacks from the same file.
 *
 * <p>Key optimizations:
 *
 * <ul>
 *   <li>Single shared FMOD system with 256 channels
 *   <li>FMOD_CREATECOMPRESSEDSAMPLE for concurrent reads
 *   <li>FMOD_OPENONLY for fast opens without prebuffering
 *   <li>FMOD_ACCURATETIME for precise seeking
 *   <li>NOSOUND output mode to avoid audio device contention
 * </ul>
 */
@Slf4j
public class FmodParallelSampleReader implements SampleReader {

    private final ExecutorService readExecutor;
    private final FmodLibrary fmod;
    private final Pointer system;
    private volatile boolean closed = false;

    /**
     * Creates a reader with dependency injection.
     *
     * @param libraryLoader The FMOD library loader
     * @param parallelism Number of parallel threads for reading
     */
    public FmodParallelSampleReader(@NonNull FmodLibraryLoader libraryLoader, int parallelism) {
        if (parallelism < 1) {
            throw new IllegalArgumentException("Parallelism must be at least 1: " + parallelism);
        }

        try {
            long startTime = System.nanoTime();

            // Load FMOD library once for all threads
            this.fmod = libraryLoader.loadAudioLibrary(FmodLibrary.class);

            // Create single shared FMOD system with many channels
            PointerByReference systemRef = new PointerByReference();
            int result = fmod.FMOD_System_Create(systemRef, FmodConstants.FMOD_VERSION);
            if (result != FmodConstants.FMOD_OK) {
                throw new AudioEngineException(
                        "Failed to create FMOD system: " + FmodError.describe(result));
            }
            this.system = systemRef.getValue();

            // Use NOSOUND output to avoid audio device contention
            result = fmod.FMOD_System_SetOutput(system, FmodConstants.FMOD_OUTPUTTYPE_NOSOUND);
            if (result != FmodConstants.FMOD_OK) {
                log.warn("Could not set NOSOUND output: {}", FmodError.describe(result));
            }

            // Set smaller DSP buffer for faster reads
            result = fmod.FMOD_System_SetDSPBufferSize(system, 256, 2);
            if (result != FmodConstants.FMOD_OK) {
                log.warn("Could not set DSP buffer size: {}", FmodError.describe(result));
            }

            // Initialize with many channels to support concurrent reads
            result = fmod.FMOD_System_Init(system, 256, FmodConstants.FMOD_INIT_NORMAL, null);
            if (result != FmodConstants.FMOD_OK) {
                fmod.FMOD_System_Release(system);
                throw new AudioEngineException(
                        "Failed to initialize FMOD system: " + FmodError.describe(result));
            }

            long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
            log.info("Created shared FMOD system with 256 channels in {}ms", elapsedMs);
        } catch (AudioEngineException e) {
            throw new RuntimeException("Failed to initialize FMOD system", e);
        }

        // Create thread pool for parallel reads
        this.readExecutor =
                Executors.newFixedThreadPool(
                        parallelism,
                        r -> {
                            Thread t = new Thread(r);
                            t.setName("FmodSampleReader-" + t.getId());
                            t.setDaemon(true);
                            return t;
                        });
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

        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        return readSamplesInternal(audioFile, startFrame, frameCount);
                    } catch (AudioReadException e) {
                        throw new CompletionException(e);
                    }
                },
                readExecutor);
    }

    private AudioData readSamplesInternal(Path audioFile, long startFrame, long frameCount)
            throws AudioReadException {

        String filePath = audioFile.toAbsolutePath().toString();
        Pointer sound = null;

        try {
            // Synchronize only the system update and sound creation
            synchronized (system) {
                // Update FMOD system (required for proper operation)
                fmod.FMOD_System_Update(system);
            }

            // Create sound with COMPRESSEDSAMPLE for concurrent reads
            PointerByReference soundRef = new PointerByReference();
            int flags =
                    FmodConstants.FMOD_CREATECOMPRESSEDSAMPLE
                            | FmodConstants.FMOD_OPENONLY
                            | FmodConstants.FMOD_ACCURATETIME;

            int result = fmod.FMOD_System_CreateSound(system, filePath, flags, null, soundRef);
            if (result != FmodConstants.FMOD_OK) {
                throw new AudioReadException(
                        "Failed to open audio file: " + FmodError.describe(result), audioFile);
            }
            sound = soundRef.getValue();

            // Get sound format info
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

            // Validate and adjust range
            if (startFrame >= totalFrames) {
                // Reading beyond EOF returns empty
                return AudioData.empty(sampleRate, channelCount, startFrame);
            }

            long actualFrameCount = Math.min(frameCount, totalFrames - startFrame);
            if (actualFrameCount <= 0) {
                return AudioData.empty(sampleRate, channelCount, startFrame);
            }

            // Seek to start position if needed
            if (startFrame > 0) {
                result =
                        fmod.FMOD_Sound_SeekData(
                                sound, (int) (startFrame * channelCount * bytesPerSample));
                if (result != FmodConstants.FMOD_OK) {
                    throw new AudioReadException(
                            "Failed to seek: " + FmodError.describe(result), audioFile);
                }
            }

            // Read the samples
            int samplesPerFrame = channelCount;
            int totalSamples = (int) (actualFrameCount * samplesPerFrame);
            int bytesNeeded = totalSamples * bytesPerSample;
            byte[] buffer = new byte[bytesNeeded];

            IntByReference bytesReadRef = new IntByReference();
            com.sun.jna.Memory memory = new com.sun.jna.Memory(bytesNeeded);
            memory.write(0, buffer, 0, bytesNeeded);
            result = fmod.FMOD_Sound_ReadData(sound, memory, bytesNeeded, bytesReadRef);

            if (result != FmodConstants.FMOD_OK && result != FmodConstants.FMOD_ERR_FILE_EOF) {
                throw new AudioReadException(
                        "Failed to read samples: " + FmodError.describe(result), audioFile);
            }

            int bytesRead = bytesReadRef.getValue();
            int samplesRead = bytesRead / bytesPerSample;
            memory.read(0, buffer, 0, bytesRead);

            // Convert bytes to normalized double samples
            double[] samples = new double[samplesRead];
            convertToDouble(buffer, samples, bitsPerSample, samplesRead);

            long framesRead = samplesRead / channelCount;
            return new AudioData(samples, sampleRate, channelCount, startFrame, framesRead);

        } finally {
            // Always release the sound object
            if (sound != null) {
                fmod.FMOD_Sound_Release(sound);
            }
        }
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
                        return getMetadataInternal(audioFile);
                    } catch (AudioReadException e) {
                        throw new CompletionException(e);
                    }
                },
                readExecutor);
    }

    private AudioMetadata getMetadataInternal(Path audioFile) throws AudioReadException {
        String filePath = audioFile.toAbsolutePath().toString();
        Pointer sound = null;

        try {
            synchronized (system) {
                fmod.FMOD_System_Update(system);
            }

            // Open with minimal flags for metadata
            PointerByReference soundRef = new PointerByReference();
            int flags = FmodConstants.FMOD_CREATECOMPRESSEDSAMPLE | FmodConstants.FMOD_OPENONLY;

            int result = fmod.FMOD_System_CreateSound(system, filePath, flags, null, soundRef);
            if (result != FmodConstants.FMOD_OK) {
                throw new AudioReadException(
                        "Failed to open audio file: " + FmodError.describe(result), audioFile);
            }
            sound = soundRef.getValue();

            // Get format info
            IntByReference soundTypeRef = new IntByReference();
            IntByReference formatRef = new IntByReference();
            IntByReference channelsRef = new IntByReference();
            IntByReference bitsRef = new IntByReference();

            result =
                    fmod.FMOD_Sound_GetFormat(sound, soundTypeRef, formatRef, channelsRef, bitsRef);
            if (result != FmodConstants.FMOD_OK) {
                throw new AudioReadException(
                        "Failed to get format: " + FmodError.describe(result), audioFile);
            }

            // Get sample rate
            var frequencyRef = new com.sun.jna.ptr.FloatByReference();
            result = fmod.FMOD_Sound_GetDefaults(sound, frequencyRef, null);
            if (result != FmodConstants.FMOD_OK) {
                throw new AudioReadException(
                        "Failed to get sample rate: " + FmodError.describe(result), audioFile);
            }

            // Get length in samples
            IntByReference lengthRef = new IntByReference();
            result = fmod.FMOD_Sound_GetLength(sound, lengthRef, FmodConstants.FMOD_TIMEUNIT_PCM);
            if (result != FmodConstants.FMOD_OK) {
                throw new AudioReadException(
                        "Failed to get length: " + FmodError.describe(result), audioFile);
            }

            // Get length in milliseconds for duration
            IntByReference msLengthRef = new IntByReference();
            result = fmod.FMOD_Sound_GetLength(sound, msLengthRef, FmodConstants.FMOD_TIMEUNIT_MS);
            if (result != FmodConstants.FMOD_OK) {
                throw new AudioReadException(
                        "Failed to get duration: " + FmodError.describe(result), audioFile);
            }

            // Build format string
            String formatStr =
                    String.format(
                            "%d Hz, %d bit, %s",
                            Math.round(frequencyRef.getValue()),
                            bitsRef.getValue(),
                            channelsRef.getValue() == 1 ? "Mono" : "Stereo");

            return new AudioMetadata(
                    Math.round(frequencyRef.getValue()), // sampleRate
                    bitsRef.getValue(), // bitsPerSample
                    channelsRef.getValue(), // channels
                    formatStr, // format string
                    lengthRef.getValue(), // frameCount
                    msLengthRef.getValue() / 1000.0 // duration in seconds
                    );

        } finally {
            if (sound != null) {
                fmod.FMOD_Sound_Release(sound);
            }
        }
    }

    private void convertToDouble(
            byte[] buffer, double[] samples, int bitsPerSample, int numSamples) {
        if (bitsPerSample == 16) {
            // Convert 16-bit PCM to normalized float
            for (int i = 0; i < numSamples; i++) {
                int byteIndex = i * 2;
                short value = (short) ((buffer[byteIndex] & 0xFF) | (buffer[byteIndex + 1] << 8));
                samples[i] = value / 32768.0;
            }
        } else if (bitsPerSample == 24) {
            // Convert 24-bit PCM to normalized float
            for (int i = 0; i < numSamples; i++) {
                int byteIndex = i * 3;
                int value =
                        (buffer[byteIndex] & 0xFF)
                                | ((buffer[byteIndex + 1] & 0xFF) << 8)
                                | (buffer[byteIndex + 2] << 16);
                // Sign extend from 24 to 32 bits
                if ((value & 0x800000) != 0) {
                    value |= 0xFF000000;
                }
                samples[i] = value / 8388608.0;
            }
        } else if (bitsPerSample == 32) {
            // Convert 32-bit PCM to normalized float
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

        // Shutdown executor
        readExecutor.shutdown();
        try {
            if (!readExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                readExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            readExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Release FMOD system
        if (system != null) {
            fmod.FMOD_System_Release(system);
            log.info("Released shared FMOD system");
        }
    }
}
