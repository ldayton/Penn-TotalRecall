package components.waveform;

import audio.signal.PixelScaler;
import audio.signal.WaveformProcessor;
import env.PreferenceKeys;
import graphics.WaveformRenderer;
import jakarta.inject.Inject;
import java.awt.AlphaComposite;
import java.awt.Image;
import java.text.DecimalFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import state.AudioState;
import state.PreferencesManager;
import ui.UiConstants;

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
public class WaveformBuffer extends Buffer {
    private static final Logger logger = LoggerFactory.getLogger(WaveformBuffer.class);

    /** Audio chunk size in seconds for waveform buffering. */
    public static final int CHUNK_SIZE_SECONDS = 10;

    private final AlphaComposite antiAliasingComposite =
            AlphaComposite.getInstance(AlphaComposite.SRC_OVER, /* larger is darker */ 0.5F);

    private final double preDataSeconds = 0.25;

    private final int numChunks;
    private final int chunkWidthInPixels;

    private final double minBand;
    private final double maxBand;

    private static volatile WaveformChunk[] chunkArray;

    private volatile boolean finish;

    private int bufferedChunkNum;
    private int bufferedHeight;

    private double biggestConsecutivePixelVals;
    private final PreferencesManager preferencesManager;
    private final AudioState audioState;
    private final PixelScaler pixelScaler;
    private final WaveformRenderer waveformRenderer;
    private final WaveformProcessor waveformProcessor;

    /**
     * Creates a buffer thread using the audio information that <code>AudioState</code> provides at
     * the time the constructor runs.
     */
    @Inject
    public WaveformBuffer(
            PreferencesManager preferencesManager,
            AudioState audioState,
            PixelScaler pixelScaler,
            WaveformRenderer waveformRenderer,
            WaveformProcessor waveformProcessor) {
        this.preferencesManager = preferencesManager;
        this.audioState = audioState;
        this.pixelScaler = pixelScaler;
        this.waveformRenderer = waveformRenderer;
        this.waveformProcessor = waveformProcessor;
        finish = false;
        numChunks = audioState.lastChunkNum() + 1;
        chunkWidthInPixels = UiConstants.zoomlessPixelsPerSecond * CHUNK_SIZE_SECONDS;
        chunkArray = new WaveformChunk[numChunks];
        bufferedChunkNum = -1;
        bufferedHeight = -1;

        // bandpass filter ranges
        double minPref =
                preferencesManager.getInt(
                        PreferenceKeys.MIN_BAND_PASS, PreferenceKeys.DEFAULT_MIN_BAND_PASS);
        double maxPref =
                preferencesManager.getInt(
                        PreferenceKeys.MAX_BAND_PASS, PreferenceKeys.DEFAULT_MAX_BAND_PASS);
        double sampleRate = audioState.getCalculator().frameRate();

        double tmpMinBand = minPref / sampleRate;
        double tmpMaxBand = maxPref / sampleRate;

        final double highestBand = 0.4999999;
        final double lowestBand = 0.0000001;
        boolean bandCorrected = false;
        if (tmpMaxBand >= 0.5) {
            tmpMaxBand = highestBand;
            bandCorrected = true;
        }
        if (tmpMinBand <= 0) {
            tmpMinBand = lowestBand;
            bandCorrected = true;
        }
        if (bandCorrected) {
            DecimalFormat format = new DecimalFormat("#");
            String message =
                    "Nyquist's Theorem won't let me filter the frequencies you have requested!\n"
                            + "Filtering "
                            + format.format(tmpMinBand * sampleRate)
                            + " Hz to "
                            + format.format(tmpMaxBand * sampleRate)
                            + " Hz instead.";
            logger.warn(message);
        }
        minBand = tmpMinBand;
        maxBand = tmpMaxBand;
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

    /** {@inheritDoc} */
    @Override
    public void finish() {
        finish = true;
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

            double[] valsToDraw = getValsToDraw(chunkNum);
            if (biggestConsecutivePixelVals <= 0) {
                biggestConsecutivePixelVals =
                        pixelScaler.getRenderingPeak(
                                valsToDraw, UiConstants.zoomlessPixelsPerSecond / 2);
            }

            // Calculate Y-axis scaling for waveform amplitude display
            double yScale =
                    waveformRenderer.calculateYScale(
                            valsToDraw, height, biggestConsecutivePixelVals);

            // Calculate start time for this chunk (for time scale labels)
            double startTimeSeconds =
                    audioState.getCalculator().framesToSec(audioState.firstFrameOfChunk(myNum));

            // Use pure Graphics2D renderer (headless-compatible)
            image =
                    waveformRenderer.renderWaveformChunk(
                            valsToDraw,
                            chunkWidthInPixels,
                            height,
                            yScale,
                            startTimeSeconds,
                            biggestConsecutivePixelVals);
        }

        private double[] getValsToDraw(int chunkNum) {
            return waveformProcessor.processAudioForDisplay(
                    audioState.getCurrentAudioFileAbsolutePath(),
                    chunkNum,
                    CHUNK_SIZE_SECONDS,
                    preDataSeconds,
                    minBand,
                    maxBand,
                    chunkWidthInPixels);
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
