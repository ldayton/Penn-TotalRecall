package state;

import audio.AudioPlayer;
import audio.AudioProgressHandler;
import audio.FmodCore;
import components.AppMenuBar;
import components.annotations.Annotation;
import components.annotations.AnnotationDisplay;
import components.annotations.AnnotationFileParser;
import components.audiofiles.AudioFile;
import components.wordpool.WordpoolDisplay;
import components.wordpool.WordpoolFileParser;
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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Stack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.OsPath;

/**
 * Injectable service that manages the essential state of the program. This replaces the static
 * CurAudio class with proper dependency injection.
 */
@Singleton
public class AudioState implements AudioProgressHandler {
    private static final Logger logger = LoggerFactory.getLogger(AudioState.class);

    /** Audio chunk size in seconds for waveform buffering. */
    public static final int CHUNK_SIZE_SECONDS = 10;

    private AudioCalculator calculator;
    private AudioPlaybackCoordinator playbackCoordinator;

    private File curAudioFile;
    private AudioPlayer player;
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
    private final FmodCore fmodCore;
    private final EventDispatchBus eventBus;
    private final WordpoolDisplay wordpoolDisplay;
    private final ProgramName programName;

    @Inject
    public AudioState(
            FmodCore fmodCore,
            EventDispatchBus eventBus,
            WordpoolDisplay wordpoolDisplay,
            ProgramName programName) {
        this.fmodCore = fmodCore;
        this.eventBus = eventBus;
        this.wordpoolDisplay = wordpoolDisplay;
        this.programName = programName;
    }

    public FmodCore getFmodCore() {
        return fmodCore;
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

            // create AudioCalculator and handle bad formats/files
            calculator = null;
            try {
                calculator = new AudioCalculator(file);
            } catch (FileNotFoundException e) {
                logger.error("Audio file not found: " + file.getAbsolutePath(), e);
                throw new RuntimeException("Audio file not found: " + file.getAbsolutePath(), e);
            } catch (IOException e) {
                logger.error("Error opening audio file: " + file.getAbsolutePath(), e);
                throw new RuntimeException(
                        "Error opening audio file: " + file.getAbsolutePath(), e);
            }

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
            AudioPlayer pp;
            try {
                pp = new AudioPlayer(fmodCore);
            } catch (Throwable e1) {
                logger.error("Cannot load audio system", e1);
                throw new RuntimeException(
                        "Cannot load audio system. You may need to reinstall "
                                + programName.toString(),
                        e1);
            }
            playbackCoordinator = new AudioPlaybackCoordinator(this, eventBus);
            pp.addListener(playbackCoordinator);
            setPlayer(pp);

            try {
                pp.open(file.getAbsolutePath());
            } catch (FileNotFoundException e) {
                logger.error("AudioPlayer: Audio file not found: " + file.getAbsolutePath(), e);
                throw new RuntimeException("Audio file not found: " + file.getAbsolutePath(), e);
            }

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
        if (player != null) {
            player.stop();
        }

        wordpoolDisplay.clearText();
        wordpoolDisplay.undistinguishAllWords();

        player = null;
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
            if (getPlayer() == null) {
                throw new IllegalStateException(badStateString);
            } else {
                return true;
            }
        } else {
            if (player != null) {
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
        if (player != null) {
            return calculator;
        } else {
            throw new IllegalStateException(badStateString);
        }
    }

    public AudioPlaybackCoordinator getListener() {
        return playbackCoordinator;
    }

    /**
     * Returns the current <code>AudioPlayer</code> that is used for audio playback.
     *
     * @throws IllegalStateException If audio is not open
     */
    public AudioPlayer getPlayer() {
        if (player == null) {
            throw new IllegalStateException(audioClosedMessage);
        }
        if (calculator != null) {
            return player;
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

    private void setPlayer(AudioPlayer player) {
        this.player = player;
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
    public void updateProgress(long frame) {
        setAudioProgressWithoutUpdatingActions(frame);
    }
}
