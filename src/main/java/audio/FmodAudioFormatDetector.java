package audio;

import components.audiofiles.AudioFile;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * FMOD-based audio format detector that replaces Java Sound format detection.
 *
 * <p>This class provides comprehensive audio format information using FMOD's format detection
 * capabilities, supporting all FMOD-compatible audio formats including various bit depths, channel
 * configurations, and encodings.
 *
 * <p>Unlike the previous Java Sound implementation, this detector supports:
 *
 * <ul>
 *   <li>Any bit depth (8-bit, 16-bit, 24-bit, 32-bit, float)
 *   <li>Any channel configuration (mono, stereo, multi-channel)
 *   <li>Compressed formats (MP3, OGG, FLAC, etc.)
 *   <li>Various audio encodings and formats
 * </ul>
 *
 * <p><strong>Thread Safety:</strong> This class is thread-safe. All methods are stateless and
 * delegate to the thread-safe {@link FmodCore} for actual format detection. The class can be safely
 * used by multiple threads concurrently, including with thread-safe {@link
 * components.audiofiles.AudioFile} instances.
 */
public class FmodAudioFormatDetector {
    private static final Logger logger = LoggerFactory.getLogger(FmodAudioFormatDetector.class);

    private final FmodCore fmodCore;

    public FmodAudioFormatDetector(FmodCore fmodCore) {
        this.fmodCore = fmodCore;
    }

    /**
     * Detects audio format information from an AudioFile.
     *
     * @param audioFile the audio file to analyze
     * @return AudioFormatInfo containing comprehensive format details
     * @throws IOException if the file cannot be loaded or format cannot be determined
     * @throws IllegalArgumentException if audioFile is null
     */
    public FmodCore.AudioFormatInfo detectFormat(AudioFile audioFile) throws IOException {
        if (audioFile == null) {
            throw new IllegalArgumentException("AudioFile cannot be null");
        }

        logger.debug("Detecting format for audio file: {}", audioFile.getAbsolutePath());

        try {
            FmodCore.AudioFormatInfo formatInfo =
                    fmodCore.detectAudioFormat(audioFile.getAbsolutePath());

            logger.debug("Format detection successful: {}", formatInfo);
            return formatInfo;

        } catch (IOException e) {
            if (Boolean.getBoolean("test.suppress.fmod.format.errors")) {
                logger.debug(
                        "Suppressed: failed to detect audio format for file: {}",
                        audioFile.getAbsolutePath(),
                        e);
            } else {
                logger.error(
                        "Failed to detect audio format for file: {}",
                        audioFile.getAbsolutePath(),
                        e);
            }
            throw e;
        }
    }

    /**
     * Detects audio format information from a file path.
     *
     * @param filePath the absolute path to the audio file
     * @return AudioFormatInfo containing comprehensive format details
     * @throws IOException if the file cannot be loaded or format cannot be determined
     * @throws IllegalArgumentException if filePath is null or empty
     */
    public FmodCore.AudioFormatInfo detectFormat(String filePath) throws IOException {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IllegalArgumentException("File path cannot be null or empty");
        }

        logger.debug("Detecting format for audio file: {}", filePath);

        try {
            FmodCore.AudioFormatInfo formatInfo = fmodCore.detectAudioFormat(filePath);

            logger.debug("Format detection successful: {}", formatInfo);
            return formatInfo;

        } catch (IOException e) {
            if (Boolean.getBoolean("test.suppress.fmod.format.errors")) {
                logger.debug("Suppressed: failed to detect audio format for file: {}", filePath, e);
            } else {
                logger.error("Failed to detect audio format for file: {}", filePath, e);
            }
            throw e;
        }
    }

    /**
     * Validates that an audio file is supported by FMOD.
     *
     * @param audioFile the audio file to validate
     * @return true if the file is supported, false otherwise
     */
    public boolean isSupportedFormat(AudioFile audioFile) {
        if (audioFile == null) {
            return false;
        }

        try {
            detectFormat(audioFile);
            return true;
        } catch (Exception e) {
            logger.debug("Audio file not supported: {}", audioFile.getAbsolutePath(), e);
            return false;
        }
    }

    /**
     * Gets a human-readable description of the audio format.
     *
     * @param audioFile the audio file to describe
     * @return format description string
     * @throws IOException if format detection fails
     */
    public String getFormatDescription(AudioFile audioFile) throws IOException {
        FmodCore.AudioFormatInfo formatInfo = detectFormat(audioFile);
        return formatInfo.getFormatDescription();
    }

    /**
     * Gets the duration of an audio file in seconds.
     *
     * @param audioFile the audio file to analyze
     * @return duration in seconds
     * @throws IOException if format detection fails
     */
    public double getDurationInSeconds(AudioFile audioFile) throws IOException {
        FmodCore.AudioFormatInfo formatInfo = detectFormat(audioFile);
        return formatInfo.getDurationInSeconds();
    }

    /**
     * Gets the number of channels in an audio file.
     *
     * @param audioFile the audio file to analyze
     * @return number of channels
     * @throws IOException if format detection fails
     */
    public int getChannelCount(AudioFile audioFile) throws IOException {
        FmodCore.AudioFormatInfo formatInfo = detectFormat(audioFile);
        return formatInfo.getChannels();
    }

    /**
     * Gets the sample rate of an audio file.
     *
     * @param audioFile the audio file to analyze
     * @return sample rate in Hz
     * @throws IOException if format detection fails
     */
    public int getSampleRate(AudioFile audioFile) throws IOException {
        FmodCore.AudioFormatInfo formatInfo = detectFormat(audioFile);
        return formatInfo.getSampleRate();
    }

    /**
     * Gets the bit depth of an audio file.
     *
     * @param audioFile the audio file to analyze
     * @return bits per sample
     * @throws IOException if format detection fails
     */
    public int getBitsPerSample(AudioFile audioFile) throws IOException {
        FmodCore.AudioFormatInfo formatInfo = detectFormat(audioFile);
        return formatInfo.getBitsPerSample();
    }

    /**
     * Gets the total number of frames in an audio file.
     *
     * @param audioFile the audio file to analyze
     * @return total number of frames
     * @throws IOException if format detection fails
     */
    public long getFrameCount(AudioFile audioFile) throws IOException {
        FmodCore.AudioFormatInfo formatInfo = detectFormat(audioFile);
        return formatInfo.getFrameLength();
    }
}
