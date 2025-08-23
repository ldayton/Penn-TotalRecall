package control;

import audio.AudioPlayer;
import audio.FmodCore;
import components.MyMenu;
import components.annotations.Annotation;
import components.annotations.AnnotationDisplay;
import components.annotations.AnnotationFileParser;
import components.audiofiles.AudioFile;
import components.audiofiles.AudioFileDisplay;
import components.waveform.WaveformBuffer;
import components.waveform.WaveformDisplay;
import components.wordpool.WordpoolDisplay;
import components.wordpool.WordpoolFileParser;
import env.PreferencesManager;
import info.Constants;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Stack;
import javax.sound.sampled.UnsupportedAudioFileException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.EventBus;
import util.OSPath;

/**
 * Injectable service that manages the essential state of the program. This replaces the static
 * CurAudio class with proper dependency injection.
 */
@Singleton
public class AudioState {
    private static final Logger logger = LoggerFactory.getLogger(AudioState.class);

    private AudioMaster master;
    private MyPrecisionListener precisionListener;

    private File curAudioFile;
    private AudioPlayer player;
    private long chunkSize;

    private int desiredLoudness = 100;

    private long framePosition;
    private long[] lastFrameArray;
    private long[] firstFrameArray;
    private int totalNumOfChunks;

    private final Stack<Long> playHistory = new Stack<Long>();

    private WaveformBuffer waveformBuffer;

    private final String audioClosedMessage = "Audio Not Open. You must check first";
    private final String badStateString =
            "ERROR: potential violation of guarantee that either master and player are both null,"
                    + " or neither is";

    private final PreferencesManager preferencesManager;
    private final FmodCore fmodCore;
    private final EventBus eventBus;

    @Inject
    public AudioState(PreferencesManager preferencesManager, FmodCore fmodCore, EventBus eventBus) {
        this.preferencesManager = preferencesManager;
        this.fmodCore = fmodCore;
        this.eventBus = eventBus;
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

            // create AudioMaster and handle bad formats/files
            master = null;
            try {
                master = new AudioMaster(file);
            } catch (FileNotFoundException e) {
                logger.error("Audio file not found: " + file.getAbsolutePath(), e);
                throw new RuntimeException("Audio file not found: " + file.getAbsolutePath(), e);
            } catch (UnsupportedAudioFileException e) {
                logger.error("Unsupported audio format: " + file.getAbsolutePath(), e);
                throw new RuntimeException("Unsupported audio format: " + e.getMessage(), e);
            } catch (IOException e) {
                logger.error("Error opening audio file: " + file.getAbsolutePath(), e);
                throw new RuntimeException(
                        "Error opening audio file: " + file.getAbsolutePath(), e);
            }

            // File loaded successfully - UI components will handle title updates
            eventBus.publish(new AudioFileSwitchedEvent(file));

            chunkSize =
                    components.waveform.WaveformBuffer.CHUNK_SIZE_SECONDS
                            * (long) master.frameRate();
            totalNumOfChunks =
                    (int) Math.ceil((double) master.durationInFrames() / (double) chunkSize);
            lastFrameArray = new long[totalNumOfChunks];
            firstFrameArray = new long[totalNumOfChunks];
            for (int i = 0; i < firstFrameArray.length; i++) {
                firstFrameArray[i] = chunkSize * i;
                if (i == firstFrameArray.length - 1) {
                    lastFrameArray[i] = master.durationInFrames() - 1;
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
                                + Constants.programName,
                        e1);
            }
            precisionListener = new MyPrecisionListener();
            pp.addListener(precisionListener);
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
                            OSPath.basename(file.getAbsolutePath())
                                    + "."
                                    + Constants.lstFileExtension);
            if (lstFile.exists()) {
                try {
                    WordpoolDisplay.distinguishAsLst(WordpoolFileParser.parse(lstFile, true));
                } catch (IOException e) {
                    logger.warn("Failed to parse LST file: " + lstFile.getAbsolutePath(), e);
                }
            }

            // fill up annotation table with existing annotations
            File tmpFile =
                    new File(
                            OSPath.basename(file.getAbsolutePath())
                                    + "."
                                    + Constants.temporaryAnnotationFileExtension);
            if (tmpFile.exists()) {
                List<Annotation> tmpAnns = AnnotationFileParser.parse(tmpFile);
                AnnotationDisplay.addAnnotations(tmpAnns);
            }

            // start new video buffers
            waveformBuffer = new WaveformBuffer(preferencesManager);
            waveformBuffer.start();

            WaveformDisplay.getInstance().startRefreshes();
        }

        MyMenu.updateActions();
    }

    /**
     * Reset the program's state by killing any threads associated with the current audio file and
     * clearing any data in memory associated with the current file.
     */
    private void reset() {
        AnnotationDisplay.removeAllAnnotations();

        // stop waveform display
        WaveformDisplay.getInstance().stopRefreshes();

        // stop audio playback
        if (player != null) {
            player.stop();
        }

        // try to terminate buffer
        if (waveformBuffer != null && waveformBuffer.isAlive()) {
            boolean terminateSuccess = false;
            try {
                if (waveformBuffer.terminateThread(250) == false) {
                    terminateSuccess = false;
                } else {
                    terminateSuccess = true;
                }
            } catch (InterruptedException e) {
                terminateSuccess = false;
            }
            if (terminateSuccess == false) {
                logger.error("could not stop buffer: " + waveformBuffer);
            }
        }

        WordpoolDisplay.clearText();
        WordpoolDisplay.undistinguishAllWords();

        waveformBuffer = null;

        player = null;
        master = null;
        precisionListener = null;

        curAudioFile = null;
        lastFrameArray = null;
        firstFrameArray = null;
        chunkSize = 0;
        framePosition = 0;
        totalNumOfChunks = 0;

        playHistory.clear();

        AudioFileDisplay.getInstance().repaint();
        AnnotationDisplay.getInstance().repaint();
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
        if (master != null) {
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
     * Returns the current <code>AudioMaster</code>.
     *
     * @throws IllegalStateException If audio is not open
     */
    public AudioMaster getMaster() {
        if (master == null) {
            throw new IllegalStateException(audioClosedMessage);
        }
        if (player != null) {
            return master;
        } else {
            throw new IllegalStateException(badStateString);
        }
    }

    public MyPrecisionListener getListener() {
        return precisionListener;
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
        if (master != null) {
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

    public void setAudioProgressAndUpdateActions(long frame) {
        if (audioOpen()) {
            framePosition = frame;
            MyMenu.updateActions();
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
        if (master != null) {}
    }

    /**
     * Returns the desired loudness for current and future audio playback.
     *
     * @return Loudness on a 0-100 scale, linear to human perception of loudness
     */
    public int getDesiredLoudness() {
        return desiredLoudness;
    }
}
