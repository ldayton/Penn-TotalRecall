package util;

import java.io.IOException;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AudioUtils {
    private static final Logger logger = LoggerFactory.getLogger(AudioUtils.class);

    /**
     * Creates an <code>AudioInputStream</code> that iterates only over the desired frames.
     *
     * <p>The start and end frames are global variables.
     *
     * @param format The format of the desired <code>AudioInputStream</code>
     * @param originalAis The <code>AudioInputStream</code> to be "cropped"
     * @return An <code>AudioInputStream</code> cropped from the original one
     * @throws IOException If an I/O exception occurs
     */
    protected AudioInputStream createAudioInputStream(
            AudioFormat format, AudioInputStream originalAis, long startFrame, long endFrame)
            throws IOException {
        AudioInputStream outputAis = AudioSystem.getAudioInputStream(format, originalAis);
        // skip to desired frame
        if (startFrame > 0 || endFrame > 0) {
            long bytesToSkip = startFrame * 2;
            if (bytesToSkip > outputAis.available()) {
                logger.error("you are trying to skip over more bytes than are available");
            } else {
                if (startFrame > 0) {
                    synchronized (outputAis) {
                        long framesActuallySkipped = outputAis.skip(bytesToSkip);
                        if (framesActuallySkipped != bytesToSkip) {
                            logger.warn(
                                    framesActuallySkipped
                                            + "/"
                                            + bytesToSkip
                                            + " bytes actually skipped");
                        }
                    }
                }
                if (endFrame > 0) {
                    long length = 0;
                    if (endFrame < 0) {
                        length = originalAis.available() - bytesToSkip;
                    } else {
                        length = endFrame - startFrame;
                    }
                    outputAis = new AudioInputStream(outputAis, outputAis.getFormat(), length);
                }
            }
        }
        return outputAis;
    }
}
