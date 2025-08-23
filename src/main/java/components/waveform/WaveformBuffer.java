package components.waveform;

import control.CurAudio;
import env.PreferencesManager;
import info.GUIConstants;
import info.MyColors;
import info.MyShapes;
import info.PreferenceKeys;
import jakarta.inject.Inject;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.Image;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import marytts.signalproc.filter.BandPassFilter;
import marytts.util.data.audio.AudioDoubleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private final DecimalFormat secFormat = new DecimalFormat("0.00s");

    private static volatile WaveformChunk[] chunkArray;

    private volatile boolean finish;

    private int bufferedChunkNum;
    private int bufferedHeight;

    private double biggestConsecutivePixelVals;
    private final PreferencesManager preferencesManager;

    /**
     * Creates a buffer thread using the audio information that <code>CurAudio</code> provides at
     * the time the constructor runs.
     */
    @Inject
    public WaveformBuffer(PreferencesManager preferencesManager) {
        this.preferencesManager = preferencesManager;
        finish = false;
        numChunks = CurAudio.lastChunkNum() + 1;
        chunkWidthInPixels = GUIConstants.zoomlessPixelsPerSecond * CHUNK_SIZE_SECONDS;
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
        double sampleRate = CurAudio.getMaster().frameRate();

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
            final int curChunkNum = CurAudio.lookupChunkNum((int) CurAudio.getAudioProgress());
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

        long lastFrame = CurAudio.lastFrameOfChunk(curChunkNum - 1);
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
                // determine yScale by finding largest value that 2 consecutive pixels will actually
                // draw at
                // larger values might exist in the audio, but over intervals too short to be be
                // visualized (0 pixels), or meaningfully visualized (1 pixel)
                // this technique is inappropriate unless the values we are working on have already
                // been smoothed
                // we exclude the first half second of audio data due to the loud beep that often
                // starts psychology experiments
                double consecutiveVals;
                for (int i = GUIConstants.zoomlessPixelsPerSecond / 2;
                        i < valsToDraw.length - 1;
                        i++) {
                    consecutiveVals = Math.min(valsToDraw[i], valsToDraw[i + 1]);
                    biggestConsecutivePixelVals =
                            Math.max(consecutiveVals, biggestConsecutivePixelVals);
                }
            }

            // determine yScale for the current component height
            double yScale = ((height / 2) - 1) / (biggestConsecutivePixelVals);
            if (Double.isInfinite(yScale) || Double.isNaN(yScale)) {
                logger.warn("yScale is infinite in magnitude, or not a number, using 0 instead");
                yScale = 0;
            }

            image = WaveformDisplay.getInstance().createImage(chunkWidthInPixels, height);
            Graphics2D g2d = (Graphics2D) image.getGraphics();

            g2d.setRenderingHints(MyShapes.getRenderingHints());
            g2d.setColor(MyColors.waveformBackground);
            g2d.fillRect(0, 0, chunkWidthInPixels, height); // fill in background color
            g2d.setColor(MyColors.waveformReferenceLineColor);
            g2d.drawLine(0, height / 2, chunkWidthInPixels, height / 2); // draw reference line

            // draw seconds line
            double counter =
                    CurAudio.getMaster()
                            .framesToSec(
                                    CurAudio.firstFrameOfChunk(
                                            myNum)); // this works because buffer size is in whole
            // seconds
            for (int i = 0; i < chunkWidthInPixels; i += GUIConstants.zoomlessPixelsPerSecond) {
                g2d.setColor(MyColors.waveformScaleLineColor);
                g2d.drawLine(i, 0, i, height - 1);
                g2d.setColor(MyColors.waveformScaleTextColor);
                g2d.drawString(secFormat.format(counter), i + 5, height - 5);
                counter++;
            }

            // actually draw the waveform (~5ms)
            g2d.setColor(MyColors.firstChannelWaveformColor);
            int topY;
            int bottomY;
            final int refLinePos = height / 2;
            for (int i = 0; i < valsToDraw.length; i++) {
                // apply yScale
                double scaledSample = valsToDraw[i] * yScale;

                // separately find wave position above and below reference line, in case we support
                // stereo audio display in the future
                topY = (int) (refLinePos - scaledSample);
                bottomY = (int) (refLinePos + scaledSample);

                g2d.drawLine(i, refLinePos, i, topY);
                g2d.drawLine(i, refLinePos, i, bottomY);
            }
        }

        private double[] getValsToDraw(int chunkNum) {
            // get samples from audio file (~1ms)
            AudioInputStream ais = null;
            try {
                ais =
                        AudioSystem.getAudioInputStream(
                                new File(CurAudio.getCurrentAudioFileAbsolutePath()));
            } catch (UnsupportedAudioFileException e) {
                logger.error("Unsupported audio file format for waveform generation", e);
            } catch (IOException e) {
                logger.error("IO error reading audio file for waveform generation", e);
            }
            int preDataSizeInFrames = 0;
            long toSkip =
                    (long)
                            (chunkNum
                                    * CHUNK_SIZE_SECONDS
                                    * CurAudio.getMaster().frameRate()
                                    * (CurAudio.getMaster().frameSizeInBytes()));
            if (chunkNum > 0) {
                preDataSizeInFrames = (int) (CurAudio.getMaster().frameRate() * preDataSeconds);
                toSkip -= (preDataSizeInFrames * (CurAudio.getMaster().frameSizeInBytes()));
            }
            long skipped = -1;
            try {
                skipped = ais.skip(toSkip);
            } catch (IOException e) {
                logger.error("IO error skipping audio data for waveform chunk", e);
            }
            if (toSkip != skipped) {
                logger.warn("skipped " + skipped + " instead of " + toSkip);
            }
            AudioDoubleDataSource adds = new AudioDoubleDataSource(ais);

            // bandpass filter (~50ms)
            BandPassFilter filter = new BandPassFilter(minBand, maxBand);
            double[] samples =
                    new double
                            [(int) (CurAudio.getMaster().frameRate() * CHUNK_SIZE_SECONDS)
                                    + preDataSizeInFrames];
            int numSamplesLeft = adds.available();

            filter.apply(adds).getData(samples);

            // make the waveform prettier by smoothing the audio data (~60ms)
            double[] copy = new double[samples.length];
            System.arraycopy(samples, 0, copy, 0, copy.length);
            final int window = 20;
            double biggestInWindow;
            int start;
            int end;
            for (int i = 0; i < samples.length; i++) {
                biggestInWindow = 0;
                start = Math.max(0, i - window);
                end = Math.min(samples.length, i + window);
                for (int j = start; j < end; j++) {
                    biggestInWindow = Math.max(biggestInWindow, Math.abs(copy[j]));
                }
                samples[i] = biggestInWindow;
            }
            copy = null;

            // extract some of the samples for representation as pixels
            double[] valsToDraw = new double[chunkWidthInPixels];
            final double sampleIncrement =
                    (samples.length - preDataSizeInFrames) / (double) (valsToDraw.length);
            for (int i = 0; i < valsToDraw.length; i++) {
                int index = (int) (i * sampleIncrement) + preDataSizeInFrames;
                if (index > numSamplesLeft - 1) {
                    break;
                }
                valsToDraw[i] = samples[index];
            }

            // make the waveform prettier by smoothing the pixels
            for (int j = 0; j < 1; j++) {
                double[] copy2 = new double[valsToDraw.length];
                System.arraycopy(valsToDraw, 0, copy2, 0, valsToDraw.length);
                for (int i = 1; i < copy2.length - 1; i++) {
                    if (copy2[i] > copy2[i - 1]) {
                        if (copy2[i] > copy2[i + 1]) {
                            valsToDraw[i] = Math.max(copy2[i + 1], copy2[i - 1]);
                        }
                    }
                }
                for (int i = 1; i < copy2.length - 1; i++) {
                    if (copy2[i] < copy2[i - 1]) {
                        if (copy2[i] < copy2[i + 1]) {
                            valsToDraw[i] = Math.min(copy2[i + 1], copy2[i - 1]);
                        }
                    }
                }
                copy2 = null;
            }

            return valsToDraw;
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
