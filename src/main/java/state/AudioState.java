package state;

import a2.AudioEngine;
import a2.AudioHandle;
import a2.AudioMetadata;
import a2.PlaybackHandle;
import a2.PlaybackListener;
import a2.PlaybackState;
import com.google.inject.Provider;
import control.AudioCalculator;
import control.AudioPlaybackCoordinator;
import env.Constants;
import env.ProgramName;
import events.AudioFileSwitchedEvent;
import events.EventDispatchBus;
import events.UIUpdateRequestedEvent;
import events.WaveformRefreshEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Stack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ui.AppMenuBar;
import ui.annotations.Annotation;
import ui.annotations.AnnotationDisplay;
import ui.annotations.AnnotationFileParser;
import ui.audiofiles.AudioFile;
import ui.wordpool.WordpoolDisplay;
import ui.wordpool.WordpoolFileParser;
import util.OsPath;

/**
 * Injectable service that manages the essential state of the program. This replaces the static
 * CurAudio class with proper dependency injection.
 */
@Singleton
public class AudioState implements PlaybackListener {
    private static final Logger logger = LoggerFactory.getLogger(AudioState.class);

    /** Audio chunk size in seconds for waveform buffering. */
    public static final int CHUNK_SIZE_SECONDS = 10;

    private AudioCalculator calculator;
    private AudioPlaybackCoordinator playbackCoordinator;

    private File curAudioFile;
    private AudioEngine audioEngine;
    private AudioHandle currentAudioHandle;
    private PlaybackHandle currentPlaybackHandle;
    private long chunkSize;

    private int desiredLoudness = 100;

    private long framePosition;
    private long[] lastFrameArray;
    private long[] firstFrameArray;
    private int totalNumOfChunks;

    private final Stack<Long> playHistory = new Stack<Long>();

    private final String audioClosedMessage = "Audio Not Open. You must check first";
    private final String badStateString =
            "ERROR: potential violation of guarantee that either calculator and player are both"
                    + " null, or neither is";
    private final EventDispatchBus eventBus;
    private final WordpoolDisplay wordpoolDisplay;
    private final Provider<AudioEngine> audioEngineProvider;

    @Inject
    public AudioState(
            EventDispatchBus eventBus,
            WordpoolDisplay wordpoolDisplay,
            ProgramName programName,
            Provider<AudioEngine> audioEngineProvider) {
        this.eventBus = eventBus;
        this.wordpoolDisplay = wordpoolDisplay;
        this.audioEngineProvider = audioEngineProvider;
    }

    /**
     * Switches all of the program's state, including display, wordpool/annotation/file lists to the
     * provided file.
     *
     * <p>This is the only thread-safe place to switch program state from one audio file to another.
     *
     * @param file The audio file to switch to, or <code>null</code> to reset the program.
     */
    public void switchFile(AudioFile file) {
        reset();

        if (file == null) {
            // Reset to default state - UI components will handle title updates
            eventBus.publish(new AudioFileSwitchedEvent(null));
        } else {
            curAudioFile = file;

            // Load audio first to get metadata - lazily initialize engine on first use
            if (audioEngine == null) {
                audioEngine = audioEngineProvider.get();
            }
            try {
                currentAudioHandle = audioEngine.loadAudio(file.getAbsolutePath());
            } catch (Exception e) {
                logger.error("Failed to load audio file: " + file.getAbsolutePath(), e);
                throw new RuntimeException(
                        "Failed to load audio file: " + file.getAbsolutePath(), e);
            }

            // Create AudioCalculator with metadata from loaded audio
            AudioMetadata metadata = audioEngine.getMetadata(currentAudioHandle);
            calculator = new AudioCalculator(file, metadata);

            // Note: AudioFileSwitchedEvent will be published after full initialization

            chunkSize = CHUNK_SIZE_SECONDS * (long) calculator.frameRate();
            totalNumOfChunks =
                    (int) Math.ceil((double) calculator.durationInFrames() / (double) chunkSize);
            lastFrameArray = new long[totalNumOfChunks];
            firstFrameArray = new long[totalNumOfChunks];
            for (int i = 0; i < firstFrameArray.length; i++) {
                firstFrameArray[i] = chunkSize * i;
                if (i == firstFrameArray.length - 1) {
                    lastFrameArray[i] = calculator.durationInFrames() - 1;
                } else {
                    lastFrameArray[i] = (chunkSize) * (i + 1) - 1;
                }
            }

            // prepare playback
            playbackCoordinator = new AudioPlaybackCoordinator(this, eventBus);
            audioEngine.addPlaybackListener(this);
            audioEngine.addPlaybackListener(playbackCoordinator);

            // add words from lst file to display
            File lstFile =
                    new File(
                            OsPath.basename(file.getAbsolutePath())
                                    + "."
                                    + Constants.lstFileExtension);
            if (lstFile.exists()) {
                try {
                    wordpoolDisplay.distinguishAsLst(WordpoolFileParser.parse(lstFile, true));
                } catch (IOException e) {
                    logger.warn("Failed to parse LST file: " + lstFile.getAbsolutePath(), e);
                }
            }

            // fill up annotation table with existing annotations
            File tmpFile =
                    new File(
                            OsPath.basename(file.getAbsolutePath())
                                    + "."
                                    + Constants.temporaryAnnotationFileExtension);
            if (tmpFile.exists()) {
                List<Annotation> tmpAnns = AnnotationFileParser.parse(tmpFile);
                AnnotationDisplay.addAnnotations(tmpAnns);
            }

            // Audio system fully initialized - notify components
            eventBus.publish(new AudioFileSwitchedEvent(file));
            eventBus.publish(new WaveformRefreshEvent(WaveformRefreshEvent.Type.START));
        }

        AppMenuBar.updateActions();
    }

    /**
     * Reset the program's state by killing any threads associated with the current audio file and
     * clearing any data in memory associated with the current file.
     */
    private void reset() {
        AnnotationDisplay.removeAllAnnotations();

        // stop waveform display
        eventBus.publish(new WaveformRefreshEvent(WaveformRefreshEvent.Type.STOP));

        // stop audio playback
        if (currentPlaybackHandle != null && audioEngine != null) {
            audioEngine.stop(currentPlaybackHandle);
            currentPlaybackHandle = null;
        }

        wordpoolDisplay.clearText();
        wordpoolDisplay.undistinguishAllWords();

        audioEngine = null;
        currentAudioHandle = null;
        currentPlaybackHandle = null;
        calculator = null;
        playbackCoordinator = null;

        curAudioFile = null;
        lastFrameArray = null;
        firstFrameArray = null;
        chunkSize = 0;
        framePosition = 0;
        totalNumOfChunks = 0;

        playHistory.clear();

        eventBus.publish(
                new UIUpdateRequestedEvent(UIUpdateRequestedEvent.Component.AUDIO_FILE_DISPLAY));
        eventBus.publish(
                new UIUpdateRequestedEvent(UIUpdateRequestedEvent.Component.ANNOTATION_DISPLAY));
        // Reset complete - UI components will handle focus updates
    }

    public long popLastPlayPos() {
        if (playHistory.isEmpty()) {
            return -1;
        } else {
            return playHistory.pop();
        }
    }

    public boolean hasLastPlayPos() {
        return playHistory.isEmpty() == false;
    }

    public void pushPlayPos(long playPos) {
        playHistory.push(playPos);
    }

    /** Returns current waveform chunk index. */
    public int lookupChunkNum(long currentFrame) {
        return (int) (currentFrame / chunkSize);
    }

    /** Returns the last waveform chunk index of the current audio file. */
    public int lastChunkNum() {
        return totalNumOfChunks - 1;
    }

    /**
     * Returns the index of the first audio frame of the provided chunk, relative to the entire
     * audio file.
     */
    public long firstFrameOfChunk(int chunkNum) {
        if (chunkNum < 0 || chunkNum > firstFrameArray.length - 1) {
            return -1;
        }
        return firstFrameArray[chunkNum];
    }

    /**
     * Returns the index of the last audio frame of the provided chunk, relative to the entire audio
     * file.
     */
    public long lastFrameOfChunk(int chunkNum) {
        if (chunkNum < 0 || chunkNum > lastFrameArray.length - 1) {
            return -1;
        }
        return lastFrameArray[chunkNum];
    }

    /**
     * Determines whether audio is currently open, or whether the program is in its blank state.
     *
     * @return <code>true</code> iff audio is open
     */
    public boolean audioOpen() {
        if (calculator != null) {
            if (getAudioEngine() == null) {
                throw new IllegalStateException(badStateString);
            } else {
                return true;
            }
        } else {
            if (audioEngine != null) {
                throw new IllegalStateException(badStateString);
            } else {
                return false;
            }
        }
    }

    /**
     * Returns the current <code>AudioCalculator</code>.
     *
     * @throws IllegalStateException If audio is not open
     */
    public AudioCalculator getCalculator() {
        if (calculator == null) {
            throw new IllegalStateException(audioClosedMessage);
        }
        if (audioEngine != null) {
            return calculator;
        } else {
            throw new IllegalStateException(badStateString);
        }
    }

    public AudioPlaybackCoordinator getListener() {
        return playbackCoordinator;
    }

    /**
     * Returns the current <code>AudioEngine</code> that is used for audio playback.
     *
     * @throws IllegalStateException If audio is not open
     */
    public AudioEngine getAudioEngine() {
        if (audioEngine == null) {
            throw new IllegalStateException(audioClosedMessage);
        }
        if (calculator != null) {
            return audioEngine;
        } else {
            throw new IllegalStateException(badStateString);
        }
    }

    /**
     * Returns the absolute path of the currently open audio file.
     *
     * @throws IllegalStateException If audio is not open
     */
    public String getCurrentAudioFileAbsolutePath() {
        if (audioOpen()) {
            return curAudioFile.getAbsolutePath();
        } else {
            throw new IllegalStateException(audioClosedMessage);
        }
    }

    /**
     * Returns the "hearing frame."
     *
     * @throws IllegalStateException If audio is not open
     */
    public long getAudioProgress() {
        if (audioOpen()) {
            return framePosition;
        } else {
            throw new IllegalStateException(audioClosedMessage);
        }
    }

    /**
     * Sets the program's opinion of the current "hearing frame".
     *
     * @param frame The "hearing frame"
     */
    public void setAudioProgressWithoutUpdatingActions(long frame) {
        if (audioOpen()) {
            framePosition = frame;
        } else {
            throw new IllegalStateException(audioClosedMessage);
        }
    }

    /**
     * Gets the current frame position without checking if audio is open. This is used internally
     * for progress tracking.
     *
     * @return The current frame position, or -1 if not set
     */
    public long getFramePosition() {
        return framePosition;
    }

    public void setAudioProgressAndUpdateActions(long frame) {
        if (audioOpen()) {
            framePosition = frame;
            AppMenuBar.updateActions();
        } else {
            throw new IllegalStateException(audioClosedMessage);
        }
    }

    /**
     * Set the desired loudness for current and future audio playback.
     *
     * <p>Should take effect immediately.
     *
     * @param val Loudness on a 0-100 scale, linear to human perception of loudness
     */
    public void updateDesiredAudioLoudness(int val) {
        desiredLoudness = val;
        if (calculator != null) {}
    }

    /**
     * Returns the desired loudness for current and future audio playback.
     *
     * @return Loudness on a 0-100 scale, linear to human perception of loudness
     */
    public int getDesiredLoudness() {
        return desiredLoudness;
    }

    @Override
    public void onProgress(PlaybackHandle playback, long positionFrames, long totalFrames) {
        setAudioProgressWithoutUpdatingActions(positionFrames);
    }

    // Convenience methods for handle-based audio operations

    /**
     * Play audio from a specific position.
     *
     * @param startFrame The frame to start playback from
     */
    public void play(long startFrame) {
        if (currentAudioHandle == null) {
            throw new IllegalStateException("No audio loaded");
        }
        // Stop any existing playback
        if (audioEngine == null) {
            audioEngine = audioEngineProvider.get();
        }
        if (currentPlaybackHandle != null) {
            audioEngine.stop(currentPlaybackHandle);
            currentPlaybackHandle = null; // Clear the handle after stopping
        }
        currentPlaybackHandle = audioEngine.play(currentAudioHandle);
        if (startFrame > 0) {
            audioEngine.seek(currentPlaybackHandle, startFrame);
        }
    }

    /**
     * Stop current playback and return the position.
     *
     * @return The position when stopped, or 0 if not playing
     */
    public long stop() {
        if (currentPlaybackHandle != null && audioEngine != null) {
            long position = audioEngine.getPosition(currentPlaybackHandle);
            audioEngine.stop(currentPlaybackHandle);
            currentPlaybackHandle = null;
            return position;
        }
        return 0;
    }

    /**
     * Pause current playback and return the position.
     *
     * @return The position when paused, or 0 if not playing
     */
    public long pause() {
        if (currentPlaybackHandle != null && audioEngine != null) {
            long position = audioEngine.getPosition(currentPlaybackHandle);
            audioEngine.pause(currentPlaybackHandle);
            return position;
        }
        return 0;
    }

    /**
     * Play a short interval (fire-and-forget).
     *
     * @param startFrame Start of the interval
     * @param endFrame End of the interval
     */
    public void playInterval(long startFrame, long endFrame) {
        if (currentAudioHandle == null) {
            throw new IllegalStateException("No audio loaded");
        }
        if (audioEngine == null) {
            audioEngine = audioEngineProvider.get();
        }
        audioEngine.play(currentAudioHandle, startFrame, endFrame);
    }

    /**
     * Check if audio is currently playing.
     *
     * @return true if playing, false otherwise
     */
    public boolean isPlaying() {
        return currentPlaybackHandle != null
                && audioEngine != null
                && audioEngine.isPlaying(currentPlaybackHandle);
    }

    /**
     * Check if audio is currently paused.
     *
     * @return true if paused, false otherwise
     */
    public boolean isPaused() {
        return currentPlaybackHandle != null
                && audioEngine != null
                && audioEngine.isPaused(currentPlaybackHandle);
    }

    /**
     * Check if audio is currently stopped.
     *
     * @return true if stopped (no active playback)
     */
    public boolean isStopped() {
        return currentPlaybackHandle == null
                || audioEngine == null
                || audioEngine.isStopped(currentPlaybackHandle);
    }

    /**
     * Get the current playback state.
     *
     * @return The playback state, or STOPPED if no playback
     */
    PlaybackState getPlaybackState() {
        if (currentPlaybackHandle != null && audioEngine != null) {
            return audioEngine.getState(currentPlaybackHandle);
        }
        return PlaybackState.STOPPED;
    }

    /**
     * Get metadata for the currently loaded audio.
     *
     * @return AudioMetadata for the current audio file
     * @throws IllegalStateException if no audio is loaded
     */
    public AudioMetadata getAudioMetadata() {
        if (currentAudioHandle == null) {
            throw new IllegalStateException("No audio loaded");
        }
        if (audioEngine == null) {
            audioEngine = audioEngineProvider.get();
        }
        return audioEngine.getMetadata(currentAudioHandle);
    }

    /**
     * Get the current audio handle.
     *
     * @return AudioHandle for the current audio file
     * @throws IllegalStateException if no audio is loaded
     */
    public AudioHandle getCurrentAudioHandle() {
        if (currentAudioHandle == null) {
            throw new IllegalStateException("No audio loaded");
        }
        return currentAudioHandle;
    }
}
