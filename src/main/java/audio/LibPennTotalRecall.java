package audio;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import control.Main;
import java.io.File;

/**
 * Direct FMOD Core JNA interface for audio playback.
 *
 * <p>Replaces the C wrapper library with direct Java calls to FMOD Core API. Maintains the same
 * interface as the original LibPennTotalRecall for compatibility.
 */
public final class LibPennTotalRecall {

    // Load FMOD library based on developer mode
    private static FMODCore loadFMODLibrary() {
        try {
            if (Main.developerMode()) {
                // In developer mode, load directly from the project filesystem
                String projectDir = System.getProperty("user.dir");
                String libraryPath = projectDir + "/src/main/resources/fmod/macos/libfmod.dylib";
                File libraryFile = new File(libraryPath);
                if (!libraryFile.exists()) {
                    throw new RuntimeException("FMOD library not found at: " + libraryPath);
                }
                System.out.println("Loading FMOD from developer path: " + libraryPath);
                return Native.loadLibrary(libraryFile.getAbsolutePath(), FMODCore.class);
            } else {
                // In production, library should be bundled with the .app
                return Native.loadLibrary("fmod", FMODCore.class);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load FMOD library", e);
        }
    }

    // FMOD Core JNA interface
    interface FMODCore extends Library {
        FMODCore INSTANCE = loadFMODLibrary();

        // FMOD System functions
        int FMOD_System_Create(PointerByReference system, int headerversion);

        int FMOD_System_Init(Pointer system, int maxchannels, int flags, Pointer extradriverdata);

        int FMOD_System_Close(Pointer system);

        int FMOD_System_Release(Pointer system);

        int FMOD_System_Update(Pointer system);

        int FMOD_System_GetVersion(Pointer system, IntByReference version);

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

    // FMOD Constants
    private static final int FMOD_VERSION = 0x00020309;
    private static final int FMOD_INIT_NORMAL = 0x00000000;
    private static final int FMOD_CREATESAMPLE = 0x00000002;
    private static final int FMOD_TIMEUNIT_PCM = 0x00000001;
    private static final int FMOD_OK = 0;

    // FMOD Core instance
    private static final FMODCore fmod = FMODCore.INSTANCE;

    // FMOD state
    private static Pointer system = null;
    private static Pointer currentSound = null;
    private static Pointer currentChannel = null;
    private static long startFrame = 0;
    private static long endFrame = 0;
    private static boolean initialized = false;
    private static boolean autoStopped = false;
    private static final Object lock = new Object();

    public static final LibPennTotalRecall instance = new LibPennTotalRecall();

    private LibPennTotalRecall() {}

    // Initialize FMOD system if not already done
    private static synchronized boolean ensureInitialized() {
        if (initialized) {
            return true;
        }

        try {
            PointerByReference systemRef = new PointerByReference();
            int result = fmod.FMOD_System_Create(systemRef, FMOD_VERSION);
            if (result != FMOD_OK) {
                return false;
            }

            system = systemRef.getValue();
            result = fmod.FMOD_System_Init(system, 32, FMOD_INIT_NORMAL, null);
            if (result != FMOD_OK) {
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
     * Tells native library to playback audio immediately.
     *
     * <p>0 return value indicates playback successful. Negative return values indicate an error.
     * Specifically: -1 - unspecified error -2 - no audio devices found -3 - unable to find or use
     * file -4 - inconsistent state (e.g. playbackInProgress())
     *
     * @param canonicalPath File path
     * @param startFrame First frame of audio in the file to render
     * @param endFrame Last frame of audio in the file to render
     * @return Return-code, see above
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
                                system, canonicalPath, FMOD_CREATESAMPLE, null, soundRef);
                if (result != FMOD_OK) {
                    return -3; // unable to find or use file
                }
                currentSound = soundRef.getValue();

                // Start playback paused
                PointerByReference channelRef = new PointerByReference();
                result =
                        fmod.FMOD_System_PlaySound(
                                system, currentSound, null, 1, channelRef); // 1 = paused
                if (result != FMOD_OK) {
                    fmod.FMOD_Sound_Release(currentSound);
                    currentSound = null;
                    return -1; // unspecified error
                }
                currentChannel = channelRef.getValue();

                // Set start position
                result =
                        fmod.FMOD_Channel_SetPosition(
                                currentChannel, startFrame, FMOD_TIMEUNIT_PCM);
                if (result != FMOD_OK) {
                    cleanup();
                    return -1; // unspecified error
                }

                // Store frame range for position-based stopping
                LibPennTotalRecall.startFrame = startFrame;
                LibPennTotalRecall.endFrame = endFrame;

                // Unpause to start playback
                result = fmod.FMOD_Channel_SetPaused(currentChannel, 0); // 0 = not paused
                if (result != FMOD_OK) {
                    cleanup();
                    return -1; // unspecified error
                }

                // Update FMOD system
                fmod.FMOD_System_Update(system);

                return 0; // success

            } catch (Exception e) {
                cleanup();
                return -1; // unspecified error
            }
        }
    }

    /**
     * Tells native library to stop audio playback immediately.
     *
     * @return The hearing frame, relative to start frame, or -1 if audio not playing
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
     * Asks the native library for the hearing frame.
     *
     * @return The hearing frame, relative to start frame, or -1 if audio not playing
     */
    public long streamPosition() {
        synchronized (lock) {
            return streamPositionInternal();
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
            int result = fmod.FMOD_Channel_GetPosition(currentChannel, position, FMOD_TIMEUNIT_PCM);
            if (result != FMOD_OK) {
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

    /** Asks the native library whether audio is currently being rendered. */
    public boolean playbackInProgress() {
        synchronized (lock) {
            return playbackInProgressInternal();
        }
    }

    private boolean playbackInProgressInternal() {
        if (currentChannel == null || autoStopped) {
            return false;
        }

        try {
            IntByReference isPlaying = new IntByReference();
            int result = fmod.FMOD_Channel_IsPlaying(currentChannel, isPlaying);
            if (result != FMOD_OK) {
                return false;
            }

            boolean playing = isPlaying.getValue() != 0;

            // Check for end-frame stopping (only if still playing)
            if (playing && endFrame > 0 && endFrame > startFrame) {
                IntByReference position = new IntByReference();
                result = fmod.FMOD_Channel_GetPosition(currentChannel, position, FMOD_TIMEUNIT_PCM);
                if (result == FMOD_OK && position.getValue() >= endFrame) {
                    checkAndAutoStop();
                    return false;
                }
            }

            return playing;
        } catch (Exception e) {
            return false;
        }
    }

    /** Returns the version of the native library being used. */
    public int getLibraryRevisionNumber() {
        synchronized (lock) {
            if (!ensureInitialized()) {
                return -1;
            }

            try {
                IntByReference version = new IntByReference();
                int result = fmod.FMOD_System_GetVersion(system, version);
                if (result != FMOD_OK) {
                    return -1;
                }
                return version.getValue();
            } catch (Exception e) {
                return -1;
            }
        }
    }

    /** Returns the name of the native library being used. */
    public String getLibraryName() {
        return "FMOD Core (JNA Direct)";
    }

    // Auto-stop helper - must be called within synchronized block
    private static void checkAndAutoStop() {
        if (!autoStopped) {
            autoStopped = true;
            if (currentChannel != null) {
                fmod.FMOD_Channel_Stop(currentChannel);
                currentChannel = null;
            }
            cleanup();
        }
    }

    // Cleanup helper - must be called within synchronized block
    private static void cleanup() {
        if (currentSound != null) {
            fmod.FMOD_Sound_Release(currentSound);
            currentSound = null;
        }
        currentChannel = null;
        endFrame = 0;
        autoStopped = false;
    }

    // Static cleanup for application shutdown
    public static void shutdown() {
        synchronized (lock) {
            cleanup();
            if (system != null) {
                fmod.FMOD_System_Close(system);
                fmod.FMOD_System_Release(system);
                system = null;
            }
            initialized = false;
        }
    }
}
