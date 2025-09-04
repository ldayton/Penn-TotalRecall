package control;

import a2.AudioMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ui.audiofiles.AudioFile;

/**
 * FMOD-based audio format detection and calculation utility that provides comprehensive information
 * about audio files using FMOD's format detection capabilities.
 *
 * <p>This class stores and determines everything the program needs to know about an audio file,
 * supporting all FMOD-compatible formats including various bit depths, channel configurations, and
 * encodings.
 *
 * <p>Audio checking policy: <code>AudioCalculator</code> constructor throws exceptions concerning
 * FMOD's inability to handle a file, as well as exceptions for this program's inability to handle a
 * format. No other checking needs to be conducted after an AudioCalculator is successfully created.
 * No other methods should throw compatibility exceptions, but should instead suppress them with
 * try/catch blocks if required by Java.
 *
 * <p>The current <code>AudioCalculator</code> should be used to perform any and all math
 * conversions related to the open audio file.
 */
public class AudioCalculator {
    private static final Logger logger = LoggerFactory.getLogger(AudioCalculator.class);

    private static final int BITS_PER_BYTE = 8;

    // from constructor
    private final AudioFile audioFile;

    // from FMOD format detection
    private long numSampleFrames;
    private int numChannels;
    private int sampleSizeInBits;
    private int frameSizeInBits;
    private float sampleRate;
    private String formatDescription;

    // computed
    private double durationInSeconds;

    /**
     * Creates an AudioCalculator from audio metadata.
     *
     * @param audioFile the file containing the audio
     * @param metadata the audio metadata from AudioEngine
     */
    public AudioCalculator(AudioFile audioFile, AudioMetadata metadata) {
        this.audioFile = audioFile;

        // Store format information from metadata
        numSampleFrames = metadata.frameCount();
        numChannels = metadata.channelCount();
        sampleSizeInBits = metadata.bitsPerSample();
        sampleRate = metadata.sampleRate();
        formatDescription = metadata.format();
        durationInSeconds = metadata.durationSeconds();

        // Compute derived values
        frameSizeInBits = sampleSizeInBits * numChannels;

        logger.debug(
                "Audio format from metadata: channels={}, sampleRate={}, bitsPerSample={},"
                        + " frames={}, duration={}s",
                numChannels,
                sampleRate,
                sampleSizeInBits,
                numSampleFrames,
                durationInSeconds);
    }

    @SuppressWarnings("unused")
    private void printInfo() {
        logger.info(
                "\n"
                        + "-- AudioCalculator --\n"
                        + "file name: {}\n"
                        + "format: {}\n"
                        + "number of channels: {}\n"
                        + "sample size in bits: {}\n"
                        + "sample rate: {}\n"
                        + "num frames: {}\n"
                        + "predicted duration: {} seconds\n",
                audioFile.getName(),
                formatDescription,
                numChannels,
                sampleSizeInBits,
                sampleRate,
                numSampleFrames,
                durationInSeconds());
    }

    public double durationInSeconds() {
        return durationInSeconds;
    }

    public long durationInFrames() {
        return numSampleFrames;
    }

    public int numChannels() {
        return numChannels;
    }

    public double frameRate() {
        return sampleRate;
    }

    public double sampleRate() {
        return sampleRate;
    }

    public int sampleSizeInBits() {
        return sampleSizeInBits;
    }

    public int sampleSizeInBytes() {
        return sampleSizeInBits / BITS_PER_BYTE;
    }

    public int frameSizeInBits() {
        return frameSizeInBits;
    }

    public int frameSizeInBytes() {
        return frameSizeInBits / BITS_PER_BYTE;
    }

    public long millisToFrames(long millis) {
        return millisToFrames((double) millis);
    }

    public long millisToFrames(double millis) {
        double sec = millis / 1000.;
        return (long) (sampleRate * sec);
    }

    public long nanosToFrames(long millis) {
        return millisToFrames(((double) millis) / 1000000);
    }

    public double framesToMillis(long frames) {
        return (framesToSec(frames) * 1000);
    }

    public double framesToSec(long frames) {
        return frames * (1 / ((double) sampleRate));
    }

    public long framesToBytes(long frames) {
        return frames * (frameSizeInBits / BITS_PER_BYTE);
    }

    public long secondsToFrames(double seconds) {
        return (long) (sampleRate * seconds);
    }

    public AudioFile getAudioFile() {
        return audioFile;
    }
}
