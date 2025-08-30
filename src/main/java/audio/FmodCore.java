package audio;

import com.sun.jna.Library;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.FloatByReference;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Direct FMOD Core JNA interface for frame-precise audio playback.
 *
 * <h3>Threading Model</h3>
 *
 * <ul>
 *   <li>Fine-grained synchronization with separate locks for different operations
 *   <li>playbackLock: Playback control operations (startPlayback, stopPlayback)
 *   <li>stateLock: State query operations (streamPosition, playbackInProgress, getSampleRate)
 *   <li>initLock: Initialization operations (ensureInitialized)
 *   <li>FMOD handles audio I/O threading internally
 *   <li>Single active playback per instance
 *   <li>Automatic end-frame stopping via position polling
 * </ul>
 *
 * <h3>Frame Addressing</h3>
 *
 * <ul>
 *   <li>Zero-based PCM sample indexing
 *   <li>Position reporting relative to startFrame
 *   <li>End frame is exclusive (plays up to, not including)
 *   <li>All positions in sample frames, not time units
 * </ul>
 *
 * <h3>Resource Management</h3>
 *
 * <ul>
 *   <li>FMOD system initialized lazily on first playback
 *   <li>Sound resources released automatically after playback
 *   <li>Environment-aware output mode (NOSOUND_NRT for CI/testing)
 *   <li>Singleton lifecycle managed by Guice DI
 * </ul>
 *
 * <h3>Error Codes</h3>
 *
 * <ul>
 *   <li>0: Success
 *   <li>-1: Unspecified error
 *   <li>-2: No audio devices available
 *   <li>-3: File not found or unusable
 *   <li>-4: Inconsistent state (already playing)
 * </ul>
 *
 * <p><strong>Thread Safety:</strong> This class is thread-safe. All public methods are properly
 * synchronized using fine-grained locks to allow concurrent access while maintaining data
 * consistency. The threading model is documented in detail above.
 */
@Singleton
public final class FmodCore {

    private static final Logger logger = LoggerFactory.getLogger(FmodCore.class);

    // FMOD system configuration constants
    private static final int FMOD_MAX_CHANNELS = 32;
    private static final int FMOD_INIT_FLAGS = 0x00000000;
    private static final int FMOD_CREATE_SAMPLE_MODE = 0x00000002;
    private static final int FMOD_CREATE_STREAM_MODE = 0x00000080;

    /** FMOD API error codes and constants. */
    private static final class FmodConstants {
        static final int VERSION = 0x00020309;
        static final int INIT_NORMAL = FMOD_INIT_FLAGS;
        static final int CREATE_SAMPLE = FMOD_CREATE_SAMPLE_MODE;
        static final int CREATE_STREAM = FMOD_CREATE_STREAM_MODE;

        @SuppressWarnings("unused")
        static final int TIMEUNIT_MS = 0x00000001;

        static final int TIMEUNIT_PCM = 0x00000002;
        static final int OK = 0;
        // Mode flags
        static final int LOOP_OFF = 0x00000001;
    }

    /** FMOD output type configuration. */
    private static final class OutputTypes {
        static final int FMOD_OUTPUTTYPE_AUTODETECT = 0;
        static final int FMOD_OUTPUTTYPE_NOSOUND_NRT = 10; // Non-real-time no sound
    }

    /** FMOD format constants for audio format detection. */
    private static final class FmodFormats {
        @SuppressWarnings("unused")
        static final int FMOD_SOUND_TYPE_UNKNOWN = 0;

        static final int FMOD_SOUND_TYPE_AIFF = 1;

        @SuppressWarnings("unused")
        static final int FMOD_SOUND_TYPE_ASF = 2;

        @SuppressWarnings("unused")
        static final int FMOD_SOUND_TYPE_DLS = 3;

        static final int FMOD_SOUND_TYPE_FLAC = 4;

        @SuppressWarnings("unused")
        static final int FMOD_SOUND_TYPE_FSB = 5;

        @SuppressWarnings("unused")
        static final int FMOD_SOUND_TYPE_IT = 6;

        @SuppressWarnings("unused")
        static final int FMOD_SOUND_TYPE_MIDI = 7;

        @SuppressWarnings("unused")
        static final int FMOD_SOUND_TYPE_MOD = 8;

        static final int FMOD_SOUND_TYPE_MPEG = 9;
        static final int FMOD_SOUND_TYPE_OGGVORBIS = 10;

        @SuppressWarnings("unused")
        static final int FMOD_SOUND_TYPE_PLAYLIST = 11;

        static final int FMOD_SOUND_TYPE_RAW = 12;

        @SuppressWarnings("unused")
        static final int FMOD_SOUND_TYPE_S3M = 13;

        @SuppressWarnings("unused")
        static final int FMOD_SOUND_TYPE_USER = 14;

        static final int FMOD_SOUND_TYPE_WAV = 15;

        @SuppressWarnings("unused")
        static final int FMOD_SOUND_TYPE_XM = 16;

        @SuppressWarnings("unused")
        static final int FMOD_SOUND_TYPE_XMA = 17;

        @SuppressWarnings("unused")
        static final int FMOD_SOUND_TYPE_AUDIOQUEUE = 18;

        @SuppressWarnings("unused")
        static final int FMOD_SOUND_TYPE_AT9 = 19;

        @SuppressWarnings("unused")
        static final int FMOD_SOUND_TYPE_VORBIS = 20;

        @SuppressWarnings("unused")
        static final int FMOD_SOUND_TYPE_MEDIA_FOUNDATION = 21;

        @SuppressWarnings("unused")
        static final int FMOD_SOUND_TYPE_MEDIACODEC = 22;

        @SuppressWarnings("unused")
        static final int FMOD_SOUND_TYPE_FADPCM = 23;

        @SuppressWarnings("unused")
        static final int FMOD_SOUND_TYPE_OPUS = 24;
    }

    /** Application-specific error codes for better error handling. */
    public enum ErrorCode {
        SUCCESS(0, "Operation completed successfully"),
        UNSPECIFIED_ERROR(-1, "Unspecified error occurred"),
        NO_AUDIO_DEVICES(-2, "No audio devices available"),
        FILE_NOT_FOUND(-3, "Audio file not found or unusable"),
        INCONSISTENT_STATE(-4, "Inconsistent state - already playing");

        private final int code;
        private final String description;

        ErrorCode(int code, String description) {
            this.code = code;
            this.description = description;
        }

        public int getCode() {
            return code;
        }

        public String getDescription() {
            return description;
        }

        public static ErrorCode fromCode(int code) {
            for (ErrorCode errorCode : values()) {
                if (errorCode.code == code) {
                    return errorCode;
                }
            }
            return UNSPECIFIED_ERROR;
        }
    }

    // FMOD Core JNA interface
    interface FMODCore extends Library {

        // FMOD System functions
        int FMOD_System_Create(PointerByReference system, int headerversion);

        int FMOD_System_Init(Pointer system, int maxchannels, int flags, Pointer extradriverdata);

        int FMOD_System_SetOutput(Pointer system, int output);

        int FMOD_System_Release(Pointer system);

        int FMOD_System_Update(Pointer system);

        // Mixer / latency queries
        int FMOD_Channel_GetDSPClock(
                Pointer channel,
                com.sun.jna.ptr.LongByReference dspclock,
                com.sun.jna.ptr.LongByReference parentclock);

        int FMOD_System_GetSoftwareFormat(
                Pointer system,
                IntByReference samplerate,
                IntByReference speakermode,
                IntByReference numrawspeakers);

        // Sound creation and management
        int FMOD_System_CreateSound(
                Pointer system,
                String name_or_data,
                int mode,
                Pointer exinfo,
                PointerByReference sound);

        int FMOD_Sound_Release(Pointer sound);

        int FMOD_Sound_GetLength(Pointer sound, IntByReference length, int lengthtype);

        int FMOD_Sound_GetDefaults(
                Pointer sound, FloatByReference frequency, IntByReference priority);

        int FMOD_Sound_GetFormat(
                Pointer sound,
                IntByReference type,
                IntByReference format,
                IntByReference channels,
                IntByReference bits);

        int FMOD_Sound_ReadData(Pointer sound, Pointer buffer, int lenbytes, IntByReference read);

        // Stream read head control for chunked decoding
        int FMOD_Sound_SeekData(Pointer sound, int pcmoffset);

        // Channel/Playback functions
        int FMOD_System_PlaySound(
                Pointer system,
                Pointer sound,
                Pointer channelgroup,
                int paused,
                PointerByReference channel);

        int FMOD_Channel_Stop(Pointer channel);

        int FMOD_Channel_SetPaused(Pointer channel, int paused);

        int FMOD_Channel_IsPlaying(Pointer channel, IntByReference isplaying);

        int FMOD_Channel_GetPosition(Pointer channel, IntByReference position, int postype);

        int FMOD_Channel_SetPosition(Pointer channel, long position, int postype);

        int FMOD_Channel_SetMode(Pointer channel, int mode);
    }

    // FMOD Core instance
    private final FMODCore fmod;

    private final AudioSystemLoader audioSystemLoader;

    // FMOD state
    private Pointer system = null;
    private Pointer currentSound = null;
    private Pointer currentChannel = null;
    private long startFrame = 0;
    private long endFrame = 0;
    private boolean initialized = false;
    private boolean autoStopped = false;
    private int configuredOutputType = OutputTypes.FMOD_OUTPUTTYPE_AUTODETECT;

    // Separate locks for different concerns to improve concurrency
    private final Object playbackLock = new Object(); // For playback control operations
    private final Object stateLock = new Object(); // For state query operations
    private final Object initLock = new Object(); // For initialization operations

    @Inject
    public FmodCore(@NonNull AudioSystemLoader audioSystemLoader) {
        this.audioSystemLoader = audioSystemLoader;
        this.fmod = audioSystemLoader.loadAudioLibrary(FMODCore.class);
    }

    // Helper methods
    private boolean playbackInProgressInternal() {
        if (currentChannel == null || autoStopped) {
            return false;
        }

        try {
            IntByReference isPlaying = new IntByReference();
            int result = fmod.FMOD_Channel_IsPlaying(currentChannel, isPlaying);
            if (result != FmodConstants.OK) {
                return false;
            }

            boolean playing = isPlaying.getValue() != 0;

            // Check for end-frame stopping (only if still playing)
            if (playing && endFrame > 0 && endFrame > startFrame) {
                // Use cached position from streamPositionInternal to avoid duplicate FMOD calls
                long currentPos = streamPositionInternal();
                if (currentPos >= 0 && currentPos >= (endFrame - startFrame)) {
                    checkAndAutoStop();
                    return false;
                }
            }

            return playing;
        } catch (Exception e) {
            return false;
        }
    }

    private void cleanup() {
        if (currentSound != null) {
            try {
                fmod.FMOD_Sound_Release(currentSound);
            } catch (Exception e) {
                logger.warn("Failed to release FMOD sound resource", e);
            } finally {
                currentSound = null;
            }
        }
        currentChannel = null;
        endFrame = 0;
        autoStopped = false;
    }

    private void checkAndAutoStop() {
        if (!autoStopped) {
            autoStopped = true;
            if (currentChannel != null) {
                try {
                    fmod.FMOD_Channel_Stop(currentChannel);
                } catch (Exception e) {
                    logger.warn("Failed to stop FMOD channel", e);
                } finally {
                    currentChannel = null;
                }
            }
            cleanup();
        }
    }

    private long streamPositionInternal() {
        if (currentChannel == null) {
            return -1;
        }

        // Check if already auto-stopped
        if (autoStopped) {
            return endFrame > 0 ? endFrame - startFrame : -1;
        }

        // 1) Query decoded position in source frames
        IntByReference position = new IntByReference();
        int result =
                fmod.FMOD_Channel_GetPosition(currentChannel, position, FmodConstants.TIMEUNIT_PCM);
        if (result != FmodConstants.OK) {
            throw new IllegalStateException("FMOD_Channel_GetPosition failed: " + result);
        }

        long currentPosSourceFrames = position.getValue();

        // Stop check uses decoded position (not latency-compensated)
        if (endFrame > 0 && endFrame > startFrame && currentPosSourceFrames >= endFrame) {
            checkAndAutoStop();
            return endFrame - startFrame; // Return relative position at stop
        }

        // 2) Compute lead time between channel DSP clock and system DSP clock
        //    leadFramesOutput = channelDSPClock - systemDSPClock (in output frames)
        com.sun.jna.ptr.LongByReference chClock = new com.sun.jna.ptr.LongByReference();
        com.sun.jna.ptr.LongByReference chParent = new com.sun.jna.ptr.LongByReference();
        result = fmod.FMOD_Channel_GetDSPClock(currentChannel, chClock, chParent);
        if (result != FmodConstants.OK) {
            throw new IllegalStateException("FMOD_Channel_GetDSPClock failed: " + result);
        }

        long leadFramesOutput = chClock.getValue() - chParent.getValue();
        if (leadFramesOutput < 0) leadFramesOutput = 0;

        // 3) Convert output lead (output frames) to source frames using rates
        int sourceRate = getSampleRate(); // from current sound
        int outputRate = sourceRate;
        try {
            IntByReference outRate = new IntByReference();
            IntByReference dummyMode = new IntByReference();
            IntByReference dummyRaw = new IntByReference();
            result = fmod.FMOD_System_GetSoftwareFormat(system, outRate, dummyMode, dummyRaw);
            if (result == FmodConstants.OK && outRate.getValue() > 0) {
                outputRate = outRate.getValue();
            }
        } catch (Exception ignored) {
            // Keep outputRate == sourceRate if query fails
        }

        long leadFramesSource = leadFramesOutput;
        if (outputRate != sourceRate) {
            // Convert with rounding to nearest
            leadFramesSource =
                    Math.round((leadFramesOutput * (double) sourceRate) / (double) outputRate);
        }

        // Clamp compensation to not exceed decoded position relative to start
        long relDecoded = currentPosSourceFrames - startFrame;
        if (relDecoded < 0) relDecoded = 0;
        if (leadFramesSource > relDecoded) {
            leadFramesSource = relDecoded;
        }

        long audibleRel = relDecoded - leadFramesSource;
        return audibleRel;
    }

    // Initialize FMOD system if not already done
    private boolean ensureInitialized() {
        synchronized (initLock) {
            if (initialized) {
                return true;
            }

            try {
                // Auto-configure output mode based on environment (unless manually configured)
                if (configuredOutputType == OutputTypes.FMOD_OUTPUTTYPE_AUTODETECT) {
                    if (!audioSystemLoader.isAudioHardwareAvailable()) {
                        configuredOutputType = OutputTypes.FMOD_OUTPUTTYPE_NOSOUND_NRT;
                        logger.info(
                                "FMOD configured for NOSOUND_NRT mode (no audio hardware"
                                        + " available)");
                    } else {
                        logger.info(
                                "FMOD configured for AUTODETECT mode (audio hardware available)");
                    }
                }

                PointerByReference systemRef = new PointerByReference();
                int result = fmod.FMOD_System_Create(systemRef, FmodConstants.VERSION);
                if (result != FmodConstants.OK) {
                    return false;
                }

                system = systemRef.getValue();

                // Set output type if configured (must be called before Init)
                if (configuredOutputType != OutputTypes.FMOD_OUTPUTTYPE_AUTODETECT) {
                    result = fmod.FMOD_System_SetOutput(system, configuredOutputType);
                    if (result != FmodConstants.OK) {
                        fmod.FMOD_System_Release(system);
                        system = null;
                        return false;
                    }
                }

                result =
                        fmod.FMOD_System_Init(
                                system, FMOD_MAX_CHANNELS, FmodConstants.INIT_NORMAL, null);
                if (result != FmodConstants.OK) {
                    fmod.FMOD_System_Release(system);
                    system = null;
                    return false;
                }

                initialized = true;
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    /**
     * Starts audio playback of a sound file from a specified frame range.
     *
     * <p>This method loads a sound file using FMOD_System_CreateSound, creates a playback channel
     * with FMOD_System_PlaySound, positions to the start frame using FMOD_Channel_SetPosition, and
     * begins playback.
     *
     * <p>The audio system will automatically stop playback when it reaches the end frame. Position
     * reporting via streamPosition() will be relative to the start frame.
     *
     * @param canonicalPath Absolute file path to the audio file to play
     * @param startFrame First audio frame to play (0-based, PCM sample index)
     * @param endFrame Last audio frame to play (exclusive, PCM sample index)
     * @return Error code: 0 = success, -1 = unspecified error, -2 = no audio devices found, -3 =
     *     unable to find or use file, -4 = inconsistent state (already playing)
     */
    public int startPlayback(String canonicalPath, long startFrame, long endFrame) {
        // Initialize first (outside of playbackLock to prevent deadlock)
        if (!ensureInitialized()) {
            return ErrorCode.NO_AUDIO_DEVICES.getCode();
        }

        synchronized (playbackLock) {
            // Check if already playing
            if (playbackInProgressInternal()) {
                return ErrorCode.INCONSISTENT_STATE.getCode();
            }

            try {
                // Stop any existing playback
                cleanup();

                // Create sound
                PointerByReference soundRef = new PointerByReference();
                int result =
                        fmod.FMOD_System_CreateSound(
                                system, canonicalPath, FmodConstants.CREATE_SAMPLE, null, soundRef);
                if (result != FmodConstants.OK) {
                    return ErrorCode.FILE_NOT_FOUND.getCode();
                }
                currentSound = soundRef.getValue();

                // Start playback paused
                PointerByReference channelRef = new PointerByReference();
                result =
                        fmod.FMOD_System_PlaySound(
                                system, currentSound, null, 1, channelRef); // 1 = paused
                if (result != FmodConstants.OK) {
                    fmod.FMOD_Sound_Release(currentSound);
                    currentSound = null;
                    return ErrorCode.UNSPECIFIED_ERROR.getCode();
                }
                currentChannel = channelRef.getValue();

                // Ensure looping is disabled for this channel
                try {
                    fmod.FMOD_Channel_SetMode(currentChannel, FmodConstants.LOOP_OFF);
                } catch (Exception e) {
                    logger.debug(
                            "FMOD_Channel_SetMode(LOOP_OFF) failed or unsupported: {}",
                            e.getMessage());
                }

                // Set start position
                result =
                        fmod.FMOD_Channel_SetPosition(
                                currentChannel, startFrame, FmodConstants.TIMEUNIT_PCM);
                if (result != FmodConstants.OK) {
                    cleanup();
                    return ErrorCode.UNSPECIFIED_ERROR.getCode();
                }

                // Store frame range for position-based stopping
                this.startFrame = startFrame;
                this.endFrame = endFrame;

                // Unpause to start playback
                result = fmod.FMOD_Channel_SetPaused(currentChannel, 0); // 0 = not paused
                if (result != FmodConstants.OK) {
                    cleanup();
                    return ErrorCode.UNSPECIFIED_ERROR.getCode();
                }

                // Update FMOD system
                fmod.FMOD_System_Update(system);

                return ErrorCode.SUCCESS.getCode();

            } catch (Exception e) {
                cleanup();
                return ErrorCode.UNSPECIFIED_ERROR.getCode();
            }
        }
    }

    /**
     * Stops audio playback immediately and returns the current playback position.
     *
     * <p>This method calls FMOD_Channel_Stop to halt playback and releases associated resources.
     * The returned position represents how many frames were played relative to the start frame
     * specified in startPlayback().
     *
     * <p>If called when no audio is playing, returns -1. This method is safe to call multiple
     * times.
     *
     * @return Current playback position in frames relative to start frame, or -1 if not playing
     */
    public long stopPlayback() {
        synchronized (playbackLock) {
            if (!playbackInProgressInternal()) {
                return -1;
            }

            try {
                long position = streamPositionInternal();
                cleanup(); // cleanup() handles all resource release
                return position;
            } catch (Exception e) {
                cleanup();
                return -1;
            }
        }
    }

    /**
     * Returns the current audio playback position in frames.
     *
     * <p>This method calls FMOD_Channel_GetPosition with FMOD_TIMEUNIT_PCM to retrieve the current
     * playback position. The position is relative to the start frame specified in startPlayback(),
     * not the absolute position in the file.
     *
     * <p>Position updates occur in real-time during playback and can be used for progress tracking,
     * synchronization, or seeking verification.
     *
     * @return Current playback position in frames relative to start frame, or -1 if not playing
     */
    public long streamPosition() {
        synchronized (stateLock) {
            return streamPositionInternal();
        }
    }

    /**
     * Determines whether audio playback is currently active.
     *
     * <p>This method calls FMOD_Channel_IsPlaying to check the current playback state and also
     * verifies that the audio position hasn't exceeded the specified end frame. The method will
     * return false if:
     *
     * <ul>
     *   <li>No audio channel is active
     *   <li>FMOD reports the channel is not playing
     *   <li>Playback has reached or exceeded the end frame
     *   <li>Auto-stop has been triggered
     * </ul>
     *
     * @return true if audio is actively playing within the specified range, false otherwise
     */
    public boolean playbackInProgress() {
        synchronized (stateLock) {
            return playbackInProgressInternal();
        }
    }

    /**
     * Gets the sample rate of the currently loaded audio file.
     *
     * @return Sample rate in Hz from the loaded audio file
     * @throws IllegalStateException if no file is loaded or sample rate cannot be determined
     */
    public int getSampleRate() {
        // No synchronization needed: currentSound is only modified under playbackLock,
        // and this method only reads it. If currentSound becomes null, throwing
        // an exception is the correct behavior.
        if (currentSound == null) {
            throw new IllegalStateException("No audio file loaded");
        }

        try {
            FloatByReference frequency = new FloatByReference();
            IntByReference priority = new IntByReference();
            int result = fmod.FMOD_Sound_GetDefaults(currentSound, frequency, priority);
            if (result == FmodConstants.OK) {
                float sampleRate = frequency.getValue();
                if (sampleRate > 0) {
                    return (int) sampleRate;
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to query sample rate from FMOD", e);
        }

        throw new IllegalStateException("FMOD returned invalid sample rate");
    }

    /**
     * Shuts down the FMOD system and releases all resources. This method should be called when the
     * FmodCore instance is no longer needed.
     *
     * <p>This method is thread-safe and properly synchronizes with all ongoing operations.
     */
    public void shutdown() {
        synchronized (initLock) {
            if (initialized && system != null) {
                try {
                    // No nested playbackLock needed: initLock prevents any other operations
                    // from running, so we can safely access all resources directly.
                    if (currentChannel != null) {
                        fmod.FMOD_Channel_Stop(currentChannel);
                        currentChannel = null;
                    }

                    if (currentSound != null) {
                        fmod.FMOD_Sound_Release(currentSound);
                        currentSound = null;
                    }

                    // Reset playback state
                    startFrame = 0;
                    endFrame = 0;
                    autoStopped = false;

                    // Release FMOD system
                    fmod.FMOD_System_Release(system);
                    system = null;
                    initialized = false;

                    logger.debug("FMOD system shutdown completed");
                } catch (Exception e) {
                    logger.warn("Error during FMOD system shutdown", e);
                }
            }
        }
    }

    /**
     * Detects audio format information from a file without starting playback.
     *
     * <p>This method loads an audio file using FMOD, queries its format information, and releases
     * the sound resource. It provides comprehensive format details including sample rate, channels,
     * bit depth, and format type.
     *
     * <p>This method is thread-safe and follows the same initialization pattern as startPlayback.
     *
     * @param canonicalPath Absolute file path to the audio file to analyze
     * @return AudioFormatInfo containing format details
     * @throws IllegalArgumentException if file path is null or empty
     * @throws IOException if file cannot be loaded or format cannot be determined
     */
    public AudioFormatInfo detectAudioFormat(String canonicalPath) throws IOException {
        if (canonicalPath == null || canonicalPath.trim().isEmpty()) {
            throw new IllegalArgumentException("File path cannot be null or empty");
        }

        // Initialize first (outside of initLock to prevent deadlock)
        if (!ensureInitialized()) {
            throw new IOException("Failed to initialize FMOD system");
        }

        // No synchronization needed: ensureInitialized() guarantees system is available
        // and no other thread can shut it down while this method runs.
        if (system == null) {
            throw new IOException("FMOD system not available");
        }

        Pointer tempSound = null;
        try {
            // Create sound for format detection only
            PointerByReference soundRef = new PointerByReference();
            int result =
                    fmod.FMOD_System_CreateSound(
                            system, canonicalPath, FmodConstants.CREATE_SAMPLE, null, soundRef);

            if (result != FmodConstants.OK) {
                throw new IOException("Failed to load audio file: " + canonicalPath);
            }

            tempSound = soundRef.getValue();

            // Get format information
            IntByReference type = new IntByReference();
            IntByReference format = new IntByReference();
            IntByReference channels = new IntByReference();
            IntByReference bits = new IntByReference();

            result = fmod.FMOD_Sound_GetFormat(tempSound, type, format, channels, bits);
            if (result != FmodConstants.OK) {
                throw new IOException("Failed to get audio format information");
            }

            // Get sample rate
            FloatByReference frequency = new FloatByReference();
            IntByReference priority = new IntByReference();
            result = fmod.FMOD_Sound_GetDefaults(tempSound, frequency, priority);
            if (result != FmodConstants.OK) {
                throw new IOException("Failed to get sample rate information");
            }

            // Get length in frames
            IntByReference length = new IntByReference();
            result = fmod.FMOD_Sound_GetLength(tempSound, length, FmodConstants.TIMEUNIT_PCM);
            if (result != FmodConstants.OK) {
                throw new IOException("Failed to get audio length information");
            }

            return new AudioFormatInfo(
                    type.getValue(),
                    format.getValue(),
                    channels.getValue(),
                    bits.getValue(),
                    (int) frequency.getValue(),
                    length.getValue());

        } finally {
            // Always release the temporary sound resource
            if (tempSound != null) {
                try {
                    fmod.FMOD_Sound_Release(tempSound);
                } catch (Exception e) {
                    logger.warn("Failed to release temporary sound resource", e);
                }
            }
        }
    }

    /**
     * Reads a chunk of PCM audio data from a file using FMOD.
     *
     * @param filePath absolute path to the audio file
     * @param chunkIndex zero-based chunk number
     * @param chunkSizeSeconds duration of each chunk in seconds
     * @param overlapSeconds seconds of overlap/pre-data for signal processing context
     * @return ChunkData containing the PCM samples and metadata
     * @throws IOException if file cannot be loaded or read
     */
    public ChunkData readAudioChunk(
            String filePath, int chunkIndex, double chunkSizeSeconds, double overlapSeconds)
            throws IOException {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IllegalArgumentException("File path cannot be null or empty");
        }
        if (chunkIndex < 0) {
            throw new IllegalArgumentException("Chunk index cannot be negative: " + chunkIndex);
        }
        if (chunkSizeSeconds <= 0) {
            throw new IllegalArgumentException("Chunk size must be > 0: " + chunkSizeSeconds);
        }
        if (overlapSeconds < 0) {
            throw new IllegalArgumentException(
                    "Overlap seconds cannot be negative: " + overlapSeconds);
        }

        if (!ensureInitialized()) {
            throw new IOException("Failed to initialize FMOD system");
        }

        Pointer tempSound = null;
        try {
            // Create sound for reading - use streaming mode for readData support
            PointerByReference soundRef = new PointerByReference();
            int result =
                    fmod.FMOD_System_CreateSound(
                            system, filePath, FmodConstants.CREATE_STREAM, null, soundRef);

            if (result != FmodConstants.OK) {
                throw new IOException(
                        "FMOD failed to load sound file: " + filePath + " (error: " + result + ")");
            }
            tempSound = soundRef.getValue();

            // Get audio format info
            IntByReference channels = new IntByReference();
            IntByReference bits = new IntByReference();
            FloatByReference frequency = new FloatByReference();

            result = fmod.FMOD_Sound_GetFormat(tempSound, null, null, channels, bits);
            if (result != FmodConstants.OK) {
                throw new IOException("Failed to get sound format");
            }

            result = fmod.FMOD_Sound_GetDefaults(tempSound, frequency, null);
            if (result != FmodConstants.OK) {
                throw new IOException("Failed to get sound defaults");
            }

            int sampleRate = Math.round(frequency.getValue());
            int numChannels = channels.getValue();
            int bytesPerSample = bits.getValue() / 8;
            int bytesPerFrame = numChannels * bytesPerSample;

            // Calculate chunk parameters
            int framesPerChunk = (int) Math.round(sampleRate * chunkSizeSeconds);
            int overlapFrames = chunkIndex > 0 ? (int) Math.round(sampleRate * overlapSeconds) : 0;
            int totalFramesNeeded = framesPerChunk + overlapFrames;
            int totalBytesNeeded = totalFramesNeeded * bytesPerFrame;

            // Calculate and seek to file position for this chunk (with pre-roll overlap)
            long startFrameForChunk = (long) chunkIndex * framesPerChunk - overlapFrames;
            if (startFrameForChunk < 0) startFrameForChunk = 0;
            result = fmod.FMOD_Sound_SeekData(tempSound, (int) startFrameForChunk);
            if (result != FmodConstants.OK) {
                throw new IOException("Failed to seek stream to frame " + startFrameForChunk);
            }

            // Allocate buffer for PCM data
            com.sun.jna.Memory buffer = new com.sun.jna.Memory(totalBytesNeeded);
            IntByReference bytesRead = new IntByReference();

            // Read the data
            result = fmod.FMOD_Sound_ReadData(tempSound, buffer, totalBytesNeeded, bytesRead);
            if (result != FmodConstants.OK) {
                throw new IOException(
                        "Failed to read audio data from sound (FMOD error: " + result + ")");
            }

            // Convert to double array
            int actualBytesRead = bytesRead.getValue();
            int actualFramesRead = actualBytesRead / bytesPerFrame;
            double[] samples = new double[actualFramesRead];

            // Convert based on bit depth
            if (bits.getValue() == 16) {
                short[] shortData = buffer.getShortArray(0, actualFramesRead);
                for (int i = 0; i < actualFramesRead; i++) {
                    samples[i] = shortData[i] / 32768.0; // Convert 16-bit to [-1, 1]
                }
            } else if (bits.getValue() == 24) {
                byte[] byteData = buffer.getByteArray(0, actualBytesRead);
                for (int i = 0, sampleIndex = 0; i < actualBytesRead; i += 3, sampleIndex++) {
                    if (sampleIndex >= samples.length) break;
                    int sample24 =
                            ((byteData[i + 2] << 16)
                                    | ((byteData[i + 1] & 0xFF) << 8)
                                    | (byteData[i] & 0xFF));
                    if (sample24 > 8388607) sample24 -= 16777216; // Convert to signed
                    samples[sampleIndex] = sample24 / 8388608.0; // Convert 24-bit to [-1, 1]
                }
            } else if (bits.getValue() == 32) {
                float[] floatData = buffer.getFloatArray(0, actualFramesRead);
                System.arraycopy(
                        floatData, 0, samples, 0, Math.min(floatData.length, samples.length));
                for (int i = 0; i < samples.length; i++) {
                    samples[i] = floatData[i]; // 32-bit float is already [-1, 1]
                }
            } else {
                throw new IOException("Unsupported bit depth: " + bits.getValue());
            }

            return new ChunkData(samples, sampleRate, numChannels, overlapFrames, actualFramesRead);

        } finally {
            if (tempSound != null) {
                try {
                    fmod.FMOD_Sound_Release(tempSound);
                } catch (Exception e) {
                    logger.warn("Failed to release temporary sound resource", e);
                }
            }
        }
    }

    /** Container for audio chunk data read from FMOD. */
    public static class ChunkData {
        public final double[] samples;
        public final int sampleRate;
        public final int channels;
        public final int overlapFrames;
        public final int totalFrames;

        public ChunkData(
                double[] samples,
                int sampleRate,
                int channels,
                int overlapFrames,
                int totalFrames) {
            this.samples = samples;
            this.sampleRate = sampleRate;
            this.channels = channels;
            this.overlapFrames = overlapFrames;
            this.totalFrames = totalFrames;
        }
    }

    /** Immutable container for audio format information detected by FMOD. */
    public static class AudioFormatInfo {
        private final int soundType;
        private final int format;
        private final int channels;
        private final int bitsPerSample;
        private final int sampleRate;
        private final long frameLength;

        public AudioFormatInfo(
                int soundType,
                int format,
                int channels,
                int bitsPerSample,
                int sampleRate,
                long frameLength) {
            this.soundType = soundType;
            this.format = format;
            this.channels = channels;
            this.bitsPerSample = bitsPerSample;
            this.sampleRate = sampleRate;
            this.frameLength = frameLength;
        }

        public int getSoundType() {
            return soundType;
        }

        public int getFormat() {
            return format;
        }

        public int getChannels() {
            return channels;
        }

        public int getBitsPerSample() {
            return bitsPerSample;
        }

        public int getSampleRate() {
            return sampleRate;
        }

        public long getFrameLength() {
            return frameLength;
        }

        public double getDurationInSeconds() {
            return (double) frameLength / sampleRate;
        }

        public int getFrameSizeInBytes() {
            return (channels * bitsPerSample) / 8;
        }

        public String getFormatDescription() {
            switch (soundType) {
                case FmodFormats.FMOD_SOUND_TYPE_WAV:
                    return "WAV";
                case FmodFormats.FMOD_SOUND_TYPE_AIFF:
                    return "AIFF";
                case FmodFormats.FMOD_SOUND_TYPE_FLAC:
                    return "FLAC";
                case FmodFormats.FMOD_SOUND_TYPE_MPEG:
                    return "MP3";
                case FmodFormats.FMOD_SOUND_TYPE_OGGVORBIS:
                    return "OGG Vorbis";
                case FmodFormats.FMOD_SOUND_TYPE_RAW:
                    return "RAW PCM";
                default:
                    return "Unknown (" + soundType + ")";
            }
        }

        @Override
        public String toString() {
            return String.format(
                    "AudioFormatInfo{type=%s, channels=%d, bits=%d, rate=%d, frames=%d,"
                            + " duration=%.2fs}",
                    getFormatDescription(),
                    channels,
                    bitsPerSample,
                    sampleRate,
                    frameLength,
                    getDurationInSeconds());
        }
    }
}
