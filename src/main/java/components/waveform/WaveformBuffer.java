package components.waveform;

import env.PreferenceKeys;
import jakarta.inject.Inject;
import java.awt.Image;
import java.text.DecimalFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import state.AudioState;
import state.PreferencesManager;
import waveform.Waveform;

/**
 * Handler for buffered portions of the waveform image.
 *
 * <p>Represents the waveform image as an array of <code>WaveformChunks</code> each containing a
 * portion of the waveform image. The chunk size is defined as CHUNK_SIZE_SECONDS, and the current
 * chunk is reported by {@link control.CurAudio}.
 *
 * <p>This class aims to keep the current chunk as well as the next/previous chunks (when available)
 * stored in the array. All other members of the array will be null, to save memory.
 */
public class WaveformBuffer extends Thread {
    private static final Logger logger = LoggerFactory.getLogger(WaveformBuffer.class);

    /** Audio chunk size in seconds for waveform buffering. */
    public static final int CHUNK_SIZE_SECONDS = 10;

    private final int numChunks;

    private static volatile WaveformChunk[] chunkArray;

    private volatile boolean finish;

    private int bufferedChunkNum;
    private int bufferedHeight;
    private final PreferencesManager preferencesManager;
    private final AudioState audioState;
    private final Waveform waveform;

    /**
     * Creates a buffer thread using the audio information that <code>AudioState</code> provides at
     * the time the constructor runs.
     */
    @Inject
    public WaveformBuffer(PreferencesManager preferencesManager, AudioState audioState) {
        this.preferencesManager = preferencesManager;
        this.audioState = audioState;
        finish = false;
        numChunks = audioState.lastChunkNum() + 1;
        chunkArray = new WaveformChunk[numChunks];
        bufferedChunkNum = -1;
        bufferedHeight = -1;

        // Calculate bandpass filter ranges
        double minPref =
                preferencesManager.getInt(
                        PreferenceKeys.MIN_BAND_PASS, PreferenceKeys.DEFAULT_MIN_BAND_PASS);
        double maxPref =
                preferencesManager.getInt(
                        PreferenceKeys.MAX_BAND_PASS, PreferenceKeys.DEFAULT_MAX_BAND_PASS);
        double sampleRate = audioState.getCalculator().frameRate();

        double minBand = minPref / sampleRate;
        double maxBand = maxPref / sampleRate;

        final double highestBand = 0.4999999;
        final double lowestBand = 0.0000001;
        boolean bandCorrected = false;
        if (maxBand >= 0.5) {
            maxBand = highestBand;
            bandCorrected = true;
        }
        if (minBand <= 0) {
            minBand = lowestBand;
            bandCorrected = true;
        }
        if (bandCorrected) {
            DecimalFormat format = new DecimalFormat("#");
            String message =
                    "Nyquist's Theorem won't let me filter the frequencies you have requested!\n"
                            + "Filtering "
                            + format.format(minBand * sampleRate)
                            + " Hz to "
                            + format.format(maxBand * sampleRate)
                            + " Hz instead.";
            logger.warn(message);
        }

        // Create waveform domain model - no DI needed, just new!
        this.waveform =
                new Waveform(
                        audioState.getCurrentAudioFileAbsolutePath(),
                        minBand,
                        maxBand,
                        audioState.getFmodCore());
    }

    /**
     * Monitors audio position and makes sure buffers are maintained for the current audio chunk
     * along with the previous and next (if available).
     */
    @Override
    public void run() {
        while (finish == false) {
            final int curChunkNum = audioState.lookupChunkNum((int) audioState.getAudioProgress());
            final int curHeight = WaveformDisplay.height();

            if (bufferedChunkNum < 0 || bufferedHeight <= 0) {
                // first run
                populateChunks(curChunkNum, curHeight);
            } else if (curHeight != bufferedHeight) {
                populateChunks(curChunkNum, curHeight);
            } else if (curChunkNum != bufferedChunkNum) {
                if (Math.abs(curChunkNum - bufferedChunkNum) == 1) {
                    intelligentlyPopulateChunks(bufferedChunkNum, curChunkNum, curHeight);
                } else {
                    populateChunks(curChunkNum, curHeight);
                }
            } else {
                // nothing has changed, nothing to do
            }

            bufferedChunkNum = curChunkNum;
            bufferedHeight = curHeight;

            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                logger.debug("Waveform buffer thread sleep interrupted", e);
            }
        }

        for (int i = 0; i < chunkArray.length; i++) {
            chunkArray[i] = null;
        }
    }

    /**
     * Returns the array containing the <code>WaveformChunks</code>.
     *
     * <p>All but two or three of the chunks will be <code>null</code>.
     *
     * @return The array of <code>WaveformChunks</code>
     */
    public static WaveformChunk[] getWaveformChunks() {
        return chunkArray;
    }

    /** Politely asks the thread to stop. */
    public void finish() {
        finish = true;
    }

    /**
     * Tries for period of time to terminate the thread.
     *
     * @param millis The minimum number of milliseconds to try terminating the thread for
     * @return Whether or not the thread was successfully terminated in the provided period
     * @throws InterruptedException If an attempt at sleeping the thread is unsuccessful
     */
    public final boolean terminateThread(int millis) throws InterruptedException {
        if (millis <= 0) {
            throw new IllegalArgumentException();
        }
        finish();
        final int iterationLength = 25;
        int counter = 0;
        while (true) {
            if (counter > millis) {
                break;
            }
            Thread.sleep(iterationLength);
            counter += iterationLength;
            if (isAlive() == false) {
                return true;
            }
        }
        return false;
    }

    /**
     * Called when there is reason to believe that some of the currently stored <code>WaveformCunks
     * </code> will still be needed even after the chunk number update is processed.
     *
     * <p>|lastChunkNum - curChunkNum| = 1
     *
     * @param lastChunkNum The chunk number for which the chunk array is already valid
     * @param curChunkNum The new chunk number that the chunk array needs to be updated for
     * @param curHeight The height of the image to be made
     */
    private void intelligentlyPopulateChunks(int lastChunkNum, int curChunkNum, int curHeight) {
        if (lastChunkNum < curChunkNum) {
            if (curChunkNum - 2 >= 0) {
                chunkArray[curChunkNum - 2] = null;
            }
            if (curChunkNum + 1 <= chunkArray.length - 1) {
                chunkArray[curChunkNum + 1] = new WaveformChunk(curChunkNum + 1, curHeight);
            }
        } else {
            if (curChunkNum + 2 <= chunkArray.length - 1) {
                chunkArray[curChunkNum + 2] = null;
            }
            if (curChunkNum - 1 >= 0) {
                chunkArray[curChunkNum - 1] = new WaveformChunk(curChunkNum - 1, curHeight);
            }
        }
    }

    /**
     * Called when the waveform chunk array will need to be revalidated from scratch.
     *
     * <p>Disposes of any data currently in the chunk array.
     *
     * @param curChunkNum The chunk number that the chunk array needs to be updated for
     * @param curHeight The height of the image to be made
     */
    private void populateChunks(int curChunkNum, int curHeight) {
        // free resources
        for (int i = 0; i < chunkArray.length; i++) {
            chunkArray[i] = null;
        }
        // fill current chunk
        chunkArray[curChunkNum] = new WaveformChunk(curChunkNum, curHeight);

        int firstPriority;
        int secondPriority;

        long lastFrame = audioState.lastFrameOfChunk(curChunkNum - 1);
        if (lastFrame >= 0 && WaveformDisplay.frameToDisplayXPixel(lastFrame) >= 0) {
            firstPriority = curChunkNum - 1;
            secondPriority = curChunkNum + 1;
        } else {
            firstPriority = curChunkNum + 1;
            secondPriority = curChunkNum - 1;
        }

        // fill first priority chunk, if it exists
        if (firstPriority >= 0 && firstPriority <= chunkArray.length - 1) {
            chunkArray[firstPriority] = new WaveformChunk(firstPriority, curHeight);
        }

        // fill second priority chunk, if it exists
        if (secondPriority >= 0 && secondPriority <= chunkArray.length - 1) {
            chunkArray[secondPriority] = new WaveformChunk(secondPriority, curHeight);
        }
    }

    /** Wrapper class for a chunk of waveform image. */
    public class WaveformChunk {

        private final int myNum;
        private final Image image;

        /**
         * Creates the <code>Image</code> of a chunk of waveform.
         *
         * @param chunkNum The chunk number whose image will be created
         * @param height The height of the image
         */
        private WaveformChunk(int chunkNum, int height) {
            myNum = chunkNum;
            image = waveform.renderChunk(chunkNum, height);
        }

        /**
         * In keeping with the Java API's recommendation, calls <code>Graphics.dispose()</code> on
         * the stored image's <code>Graphics</code> context.
         */
        public void dispose() {
            image.getGraphics().dispose();
        }

        /**
         * Getter for the chunk number of this part of the waveform.
         *
         * @return The chunk number
         */
        public int getNum() {
            return myNum;
        }

        /**
         * Getter for the <code>Image</code> of this object's chunk of the waveform.
         *
         * @return The <code>Image</code> of the waveform, appropriate for double-buffering
         */
        public Image getImage() {
            return image;
        }

        /** {@inheritDoc} */
        @Override
        public String toString() {
            return "WaveformChunk #" + myNum;
        }
    }
}
