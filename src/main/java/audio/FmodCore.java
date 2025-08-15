package audio;

import com.sun.jna.Library;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Direct FMOD Core JNA interface for audio playback.
 *
 * <p>Replaces the C wrapper library with direct Java calls to FMOD Core API. Maintains the same
 * interface as the original LibPennTotalRecall for compatibility.
 *
 * <p>This wrapper provides a simplified Java interface to FMOD Core's audio playback capabilities,
 * specifically designed for precise frame-based audio playback with real-time position tracking.
 * All audio operations are thread-safe and positions are reported in PCM sample frames.
 */
@Singleton
public final class FmodCore {

    private static final Logger logger = LoggerFactory.getLogger(FmodCore.class);

    private static final class FmodConstants {
        static final int VERSION = 0x00020309;
        static final int INIT_NORMAL = 0x00000000;
        static final int CREATE_SAMPLE = 0x00000002;
        static final int TIMEUNIT_MS = 0x00000001;
        static final int TIMEUNIT_PCM = 0x00000002;
        static final int OK = 0;
    }

    private static final class OutputTypes {
        static final int FMOD_OUTPUTTYPE_AUTODETECT = 0;
        static final int FMOD_OUTPUTTYPE_NOSOUND_NRT = 10; // Non-real-time no sound
    }

    private static final class ErrorCodes {
        static final int SUCCESS = 0;
        static final int UNSPECIFIED_ERROR = -1;
        static final int NO_AUDIO_DEVICES = -2;
        static final int FILE_NOT_FOUND = -3;
        static final int INCONSISTENT_STATE = -4;
    }

    // FMOD Core JNA interface
    interface FMODCore extends Library {

        // FMOD System functions
        int FMOD_System_Create(PointerByReference system, int headerversion);

        int FMOD_System_Init(Pointer system, int maxchannels, int flags, Pointer extradriverdata);

        int FMOD_System_SetOutput(Pointer system, int output);

        int FMOD_System_Release(Pointer system);

        int FMOD_System_Update(Pointer system);

        // Sound creation and management
        int FMOD_System_CreateSound(
                Pointer system,
                String name_or_data,
                int mode,
                Pointer exinfo,
                PointerByReference sound);

        int FMOD_Sound_Release(Pointer sound);

        int FMOD_Sound_GetLength(Pointer sound, IntByReference length, int lengthtype);

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
    }

    // FMOD Core instance
    private final FMODCore fmod;

    private final env.AppConfig appConfig;

    // FMOD state
    private Pointer system = null;
    private Pointer currentSound = null;
    private Pointer currentChannel = null;
    private long startFrame = 0;
    private long endFrame = 0;
    private boolean initialized = false;
    private boolean autoStopped = false;
    private int configuredOutputType = OutputTypes.FMOD_OUTPUTTYPE_AUTODETECT;
    private final Object lock = new Object();

    @Inject
    public FmodCore(@NonNull env.AppConfig appConfig, @NonNull FmodLibraryLoader libraryLoader) {
        this.appConfig = appConfig;
        this.fmod = libraryLoader.loadLibrary(FMODCore.class);
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
                IntByReference position = new IntByReference();
                result =
                        fmod.FMOD_Channel_GetPosition(
                                currentChannel, position, FmodConstants.TIMEUNIT_PCM);
                if (result == FmodConstants.OK && position.getValue() >= endFrame) {
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
            fmod.FMOD_Sound_Release(currentSound);
            currentSound = null;
        }
        currentChannel = null;
        endFrame = 0;
        autoStopped = false;
    }

    private void checkAndAutoStop() {
        if (!autoStopped) {
            autoStopped = true;
            if (currentChannel != null) {
                fmod.FMOD_Channel_Stop(currentChannel);
                currentChannel = null;
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

        try {
            IntByReference position = new IntByReference();
            int result =
                    fmod.FMOD_Channel_GetPosition(
                            currentChannel, position, FmodConstants.TIMEUNIT_PCM);
            if (result != FmodConstants.OK) {
                return -1;
            }

            long currentPos = position.getValue();

            // Check if we've reached the end frame and auto-stop
            if (endFrame > 0 && endFrame > startFrame && currentPos >= endFrame) {
                checkAndAutoStop();
                return endFrame - startFrame; // Return relative position at stop
            }

            // Return position relative to start frame
            return currentPos - startFrame;
        } catch (Exception e) {
            return -1;
        }
    }

    // Initialize FMOD system if not already done
    private synchronized boolean ensureInitialized() {
        if (initialized) {
            return true;
        }

        try {
            // Auto-configure output mode based on environment (unless manually configured)
            if (configuredOutputType == OutputTypes.FMOD_OUTPUTTYPE_AUTODETECT) {
                if (!appConfig.isAudioHardwareAvailable()) {
                    configuredOutputType = OutputTypes.FMOD_OUTPUTTYPE_NOSOUND_NRT;
                    logger.info(
                            "FMOD configured for NOSOUND_NRT mode (no audio hardware available)");
                } else {
                    logger.info("FMOD configured for AUTODETECT mode (audio hardware available)");
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

            result = fmod.FMOD_System_Init(system, 32, FmodConstants.INIT_NORMAL, null);
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
        synchronized (lock) {
            if (!ensureInitialized()) {
                return -2; // no audio devices found
            }

            // Check if already playing
            if (playbackInProgressInternal()) {
                return -4; // inconsistent state
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
                    return ErrorCodes.FILE_NOT_FOUND;
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
                    return ErrorCodes.UNSPECIFIED_ERROR;
                }
                currentChannel = channelRef.getValue();

                // Set start position
                result =
                        fmod.FMOD_Channel_SetPosition(
                                currentChannel, startFrame, FmodConstants.TIMEUNIT_PCM);
                if (result != FmodConstants.OK) {
                    cleanup();
                    return ErrorCodes.UNSPECIFIED_ERROR;
                }

                // Store frame range for position-based stopping
                this.startFrame = startFrame;
                this.endFrame = endFrame;

                // Unpause to start playback
                result = fmod.FMOD_Channel_SetPaused(currentChannel, 0); // 0 = not paused
                if (result != FmodConstants.OK) {
                    cleanup();
                    return ErrorCodes.UNSPECIFIED_ERROR;
                }

                // Update FMOD system
                fmod.FMOD_System_Update(system);

                return ErrorCodes.SUCCESS;

            } catch (Exception e) {
                cleanup();
                return ErrorCodes.UNSPECIFIED_ERROR;
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
        synchronized (lock) {
            if (!playbackInProgressInternal()) {
                return -1;
            }

            try {
                long position = streamPositionInternal();

                if (currentChannel != null) {
                    fmod.FMOD_Channel_Stop(currentChannel);
                    currentChannel = null;
                }

                cleanup();

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
        synchronized (lock) {
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
        synchronized (lock) {
            return playbackInProgressInternal();
        }
    }
}
