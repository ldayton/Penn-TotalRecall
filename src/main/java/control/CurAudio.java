package control;

import audio.AudioPlayer;
import components.audiofiles.AudioFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Static-only class that stores the essential state of the program.
 *
 * @deprecated This class is being replaced by AudioState for proper dependency injection. Use
 *     AudioState instead for new code.
 */
public class CurAudio {
    private static final Logger logger = LoggerFactory.getLogger(CurAudio.class);

    private static AudioState audioState;

    /** Prevent instantiation. */
    private CurAudio() {}

    /**
     * Initialize the static CurAudio with the injected AudioState instance. This allows for gradual
     * migration from static access to dependency injection.
     */
    public static void initialize(AudioState audioState) {
        CurAudio.audioState = audioState;
    }

    private static AudioState getAudioState() {
        if (audioState == null) {
            throw new IllegalStateException(
                    "CurAudio not initialized. Call CurAudio.initialize() first.");
        }
        return audioState;
    }

    /**
     * Switches all of the program's state, including display, wordpool/annotation/file lists to the
     * provided file.
     *
     * <p>This is the only thread-safe place to switch program state from one audio file to another.
     *
     * @param file The audio file to switch to, or <code>null</code> to reset the program.
     */
    public static void switchFile(AudioFile file) {
        getAudioState().switchFile(file);
    }

    /**
     * Reset the program's state by killing any threads associated with the current audio file and
     * clearing any data in memory associated with the current file.
     */
    private static void reset() {
        // This method is now private and only used internally by AudioState
        // The reset functionality is handled by AudioState.reset()
    }

    public static long popLastPlayPos() {
        return getAudioState().popLastPlayPos();
    }

    public static boolean hasLastPlayPos() {
        return getAudioState().hasLastPlayPos();
    }

    public static void pushPlayPos(long playPos) {
        getAudioState().pushPlayPos(playPos);
    }

    /** Returns current waveform chunk index. */
    public static int lookupChunkNum(long currentFrame) {
        return getAudioState().lookupChunkNum(currentFrame);
    }

    /** Returns the last waveform chunk index of the current audio file. */
    public static int lastChunkNum() {
        return getAudioState().lastChunkNum();
    }

    /**
     * Returns the index of the first audio frame of the provided chunk, relative to the entire
     * audio file.
     */
    public static long firstFrameOfChunk(int chunkNum) {
        return getAudioState().firstFrameOfChunk(chunkNum);
    }

    /**
     * Returns the index of the last audio frame of the provided chunk, relative to the entire audio
     * file.
     */
    public static long lastFrameOfChunk(int chunkNum) {
        return getAudioState().lastFrameOfChunk(chunkNum);
    }

    /**
     * Determines whether audio is currently open, or whether the program is in its blank state.
     *
     * @return <code>true</code> iff audio is open
     */
    public static boolean audioOpen() {
        return getAudioState().audioOpen();
    }

    /**
     * Returns the current <code>AudioMaster</code>.
     *
     * @throws IllegalStateException If audio is not open
     */
    public static AudioMaster getMaster() {
        return getAudioState().getMaster();
    }

    public static MyPrecisionListener getListener() {
        return getAudioState().getListener();
    }

    /**
     * Returns the current <code>AudioPlayer</code> that is used for audio playback.
     *
     * @throws IllegalStateException If audio is not open
     */
    public static AudioPlayer getPlayer() {
        return getAudioState().getPlayer();
    }

    /**
     * Returns the absolute path of the currently open audio file.
     *
     * @throws IllegalStateException If audio is not open
     */
    public static String getCurrentAudioFileAbsolutePath() {
        return getAudioState().getCurrentAudioFileAbsolutePath();
    }

    private static void setPlayer(AudioPlayer player) {
        // This method is now private and only used internally by AudioState
        // The setPlayer functionality is handled by AudioState.setPlayer()
    }

    /**
     * Returns the "hearing frame."
     *
     * @throws IllegalStateException If audio is not open
     */
    public static long getAudioProgress() {
        return getAudioState().getAudioProgress();
    }

    /**
     * Sets the program's opinion of the current "hearing frame".
     *
     * @param frame The "hearing frame"
     */
    public static void setAudioProgressWithoutUpdatingActions(long frame) {
        getAudioState().setAudioProgressWithoutUpdatingActions(frame);
    }

    public static void setAudioProgressAndUpdateActions(long frame) {
        getAudioState().setAudioProgressAndUpdateActions(frame);
    }

    /**
     * Set the desired loudness for current and future audio playback.
     *
     * <p>Should take effect immediately.
     *
     * @param val Loudness on a 0-100 scale, linear to human perception of loudness
     */
    public static void updateDesiredAudioLoudness(int val) {
        getAudioState().updateDesiredAudioLoudness(val);
    }

    /**
     * Returns the desired loudness for current and future audio playback.
     *
     * @return Loudness on a 0-100 scale, linear to human perception of loudness
     */
    public static int getDesiredLoudness() {
        return getAudioState().getDesiredLoudness();
    }
}
