package control;

import audio.AudioPlayer;
import audio.FmodCore;
import components.MyFrame;
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
import di.GuiceBootstrap;
import info.Constants;
import info.GUIConstants;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Stack;
import javax.sound.sampled.UnsupportedAudioFileException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.GiveMessage;
import util.OSPath;

/** Static-only class that stores the eseential state of the program. */
public class CurAudio {
    private static final Logger logger = LoggerFactory.getLogger(CurAudio.class);

    private static AudioMaster master;
    private static MyPrecisionListener precisionListener;

    private static File curAudioFile;
    private static AudioPlayer player;
    private static long chunkSize;

    private static int desiredLoudness = 100;

    private static long framePosition;
    private static long[] lastFrameArray;
    private static long[] firstFrameArray;
    private static int totalNumOfChunks;

    private static final Stack<Long> playHistory = new Stack<Long>();

    private static WaveformBuffer waveformBuffer;

    private static final String audioClosedMessage = "Audio Not Open. You must check first";
    private static final String badStateString =
            "ERROR: potential violation of guarantee that either master and player are both null,"
                    + " or neither is";

    /** Prevent instantiation. */
    private CurAudio() {}

    /**
     * Switches all of the program's state, including display, wordpool/annotation/file lists to the
     * provided file.
     *
     * <p>This is the only thread-safe place to switch program state from one audio file to another.
     *
     * @param file The audio file to swtich to, or <code>null</code> to reset the program.
     */
    public static void switchFile(AudioFile file) {
        reset();

        if (file == null) {
            MyFrame.getInstance().setTitle(GUIConstants.defaultFrameTitle);
        } else {
            curAudioFile = file;

            // create AudioMaster and handle bad formats/files
            master = null;
            boolean success = false;
            try {
                master = new AudioMaster(file);
                success = true;
            } catch (FileNotFoundException e) {
                logger.error("Audio file not found: " + file.getAbsolutePath(), e);
                GiveMessage.errorMessage("Audio file not found!");
            } catch (UnsupportedAudioFileException e) {
                logger.error("Unsupported audio format: " + file.getAbsolutePath(), e);
                GiveMessage.errorMessage("Unsupported audio format!\n" + e.getMessage());
            } catch (IOException e) {
                logger.error("Error opening audio file: " + file.getAbsolutePath(), e);
                GiveMessage.errorMessage("Error opening audio file!");
            }
            if (!success) {
                switchFile(null);
                logger.error("audio switch not successful. resetting current audio.");
                return;
            }

            // change frame title to display current file info
            MyFrame.getInstance()
                    .setTitle(GUIConstants.defaultFrameTitle + " - " + curAudioFile.getPath());

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
            AudioPlayer pp = null;
            try {
                // Require DI-managed FmodCore - application must be initialized with Guice
                FmodCore fmodCore = GuiceBootstrap.getInjectedInstance(FmodCore.class);
                if (fmodCore == null) {
                    throw new IllegalStateException(
                            "FmodCore not available via dependency injection. Application must be"
                                    + " initialized with Guice before using audio.");
                }
                pp = new AudioPlayer(fmodCore);
            } catch (Throwable e1) {
                logger.error("Cannot load audio system", e1);
                GiveMessage.errorMessage(
                        "Cannot load audio system.\nYou may need to reinstall "
                                + Constants.programName
                                + ".");
                reset();
            }
            precisionListener = new MyPrecisionListener();
            pp.addListener(precisionListener);
            setPlayer(pp);

            success = false;
            try {
                pp.open(file.getAbsolutePath());
                success = true;
            } catch (FileNotFoundException e) {
                logger.error("AudioPlayer: Audio file not found: " + file.getAbsolutePath(), e);
                GiveMessage.errorMessage("Audio file not found!");
            }
            if (!success) {
                switchFile(null);
                logger.error("AudioPlayer.open() not successful. resetting current audio.");
                return;
            }

            // add words from lst file to display
            File lstFile =
                    new File(
                            OSPath.basename(file.getAbsolutePath())
                                    + "."
                                    + Constants.lstFileExtension);
            if (lstFile.exists()) {
                try {
                    // refuse inter-version resuming of files
                    //					BufferedReader reader = new BufferedReader(new FileReader(lstFile));
                    //					String curLine;
                    //					while((curLine = reader.readLine()) != null) {
                    //
                    //					}
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
            waveformBuffer = new WaveformBuffer();
            waveformBuffer.start();

            WaveformDisplay.getInstance().startRefreshes();
        }

        MyMenu.updateActions();
    }

    /**
     * Reset the program's state by killing any threads associated with the current audio file and
     * clearing any data in memory associated with the current file.
     */
    private static void reset() {
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
        MyFrame.getInstance().requestFocusInWindow();
    }

    public static long popLastPlayPos() {
        if (playHistory.isEmpty()) {
            return -1;
        } else {
            return playHistory.pop();
        }
    }

    public static boolean hasLastPlayPos() {
        return playHistory.isEmpty() == false;
    }

    public static void pushPlayPos(long playPos) {
        playHistory.push(playPos);
    }

    /** Returns current waveform chunk index. */
    public static int lookupChunkNum(long currentFrame) {
        return (int) (currentFrame / chunkSize);
    }

    /** Returns the last waveform chunk index of the current audio file. */
    public static int lastChunkNum() {
        return totalNumOfChunks - 1;
    }

    /**
     * Returns the index of the first audio frame of the provided chunk, relative to the entire
     * audio file.
     */
    public static long firstFrameOfChunk(int chunkNum) {
        if (chunkNum < 0 || chunkNum > firstFrameArray.length - 1) {
            return -1;
        }
        return firstFrameArray[chunkNum];
    }

    /**
     * Returns the index of the last audio frame of the provided chunk, relative to the entire audio
     * file.
     */
    public static long lastFrameOfChunk(int chunkNum) {
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
    public static boolean audioOpen() {
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
    public static AudioMaster getMaster() {
        if (master == null) {
            throw new IllegalStateException(audioClosedMessage);
        }
        if (player != null) {
            return master;
        } else {
            throw new IllegalStateException(badStateString);
        }
    }

    public static MyPrecisionListener getListener() {
        return precisionListener;
    }

    /**
     * Returns the current <code>AudioPlayer</code> that is used for audio playback.
     *
     * @throws IllegalStateException If audio is not open
     */
    public static AudioPlayer getPlayer() {
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
    public static String getCurrentAudioFileAbsolutePath() {
        if (audioOpen()) {
            return curAudioFile.getAbsolutePath();
        } else {
            throw new IllegalStateException(audioClosedMessage);
        }
    }

    private static void setPlayer(AudioPlayer player) {
        CurAudio.player = player;
    }

    /**
     * Returns the "hearing frame."
     *
     * @throws IllegalStateException If audio is not open
     */
    public static long getAudioProgress() {
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
    public static void setAudioProgressWithoutUpdatingActions(long frame) {
        if (audioOpen()) {
            framePosition = frame;
        } else {
            throw new IllegalStateException(audioClosedMessage);
        }
    }

    public static void setAudioProgressAndUpdateActions(long frame) {
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
    public static void updateDesiredAudioLoudness(int val) {
        desiredLoudness = val;
        if (master != null) {}
    }

    /**
     * Returns the desired loudness for current and future audio playback.
     *
     * @return Loudness on a 0-100 scale, linear to human perception of loudness
     */
    public static int getDesiredLoudness() {
        return desiredLoudness;
    }
}
