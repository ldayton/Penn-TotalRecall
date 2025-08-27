package audio;

import components.audiofiles.AudioFile;
import components.audiofiles.AudioFile.AudioFilePathException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Tests for FmodAudioFormatDetector functionality.
 */
class FmodAudioFormatDetectorTest {

    private static FmodAudioFormatDetector detector;
    private static File testAudioFile;

    @BeforeAll
    static void setup() throws Exception {
        // Initialize dependencies
        env.AppConfig appConfig = new env.AppConfig();
        env.Platform platform = new env.Platform();
        AudioSystemManager audioManager = new AudioSystemManager(appConfig, platform);
        FmodCore fmodCore = new FmodCore(audioManager);
        detector = new FmodAudioFormatDetector(fmodCore);

        // Use the known sample file
        testAudioFile = new File("packaging/samples/sample.wav");
        assertTrue(testAudioFile.exists(), "Test audio file not found: " + testAudioFile.getAbsolutePath());
    }

    @Test
    void testDetectFormatWithAudioFile() throws IOException, AudioFilePathException {
        AudioFile audioFile = new AudioFile(testAudioFile.getAbsolutePath());
        FmodCore.AudioFormatInfo formatInfo = detector.detectFormat(audioFile);

        assertNotNull(formatInfo);
        assertTrue(formatInfo.getSampleRate() > 0);
        assertTrue(formatInfo.getChannels() > 0);
        assertTrue(formatInfo.getBitsPerSample() > 0);
        assertTrue(formatInfo.getFrameLength() > 0);
        assertTrue(formatInfo.getDurationInSeconds() > 0);
        assertNotNull(formatInfo.getFormatDescription());
    }

    @Test
    void testDetectFormatWithFilePath() throws IOException {
        FmodCore.AudioFormatInfo formatInfo = detector.detectFormat(testAudioFile.getAbsolutePath());

        assertNotNull(formatInfo);
        assertTrue(formatInfo.getSampleRate() > 0);
        assertTrue(formatInfo.getChannels() > 0);
        assertTrue(formatInfo.getBitsPerSample() > 0);
        assertTrue(formatInfo.getFrameLength() > 0);
        assertTrue(formatInfo.getDurationInSeconds() > 0);
        assertNotNull(formatInfo.getFormatDescription());
    }

    @Test
    void testIsSupportedFormat() throws AudioFilePathException {
        AudioFile audioFile = new AudioFile(testAudioFile.getAbsolutePath());
        assertTrue(detector.isSupportedFormat(audioFile));
    }

    @Test
    void testIsSupportedFormatWithNonExistentFile() throws AudioFilePathException {
        AudioFile nonExistentFile = new AudioFile("/path/to/nonexistent/file.wav");
        assertFalse(detector.isSupportedFormat(nonExistentFile));
    }

    @Test
    void testGetFormatDescription() throws IOException, AudioFilePathException {
        AudioFile audioFile = new AudioFile(testAudioFile.getAbsolutePath());
        String description = detector.getFormatDescription(audioFile);
        assertNotNull(description);
        assertFalse(description.isEmpty());
    }

    @Test
    void testGetDurationInSeconds() throws IOException, AudioFilePathException {
        AudioFile audioFile = new AudioFile(testAudioFile.getAbsolutePath());
        double duration = detector.getDurationInSeconds(audioFile);
        assertTrue(duration > 0);
    }

    @Test
    void testGetChannelCount() throws IOException, AudioFilePathException {
        AudioFile audioFile = new AudioFile(testAudioFile.getAbsolutePath());
        int channels = detector.getChannelCount(audioFile);
        assertTrue(channels > 0);
    }

    @Test
    void testGetSampleRate() throws IOException, AudioFilePathException {
        AudioFile audioFile = new AudioFile(testAudioFile.getAbsolutePath());
        int sampleRate = detector.getSampleRate(audioFile);
        assertTrue(sampleRate > 0);
    }

    @Test
    void testGetBitsPerSample() throws IOException, AudioFilePathException {
        AudioFile audioFile = new AudioFile(testAudioFile.getAbsolutePath());
        int bitsPerSample = detector.getBitsPerSample(audioFile);
        assertTrue(bitsPerSample > 0);
    }

    @Test
    void testGetFrameCount() throws IOException, AudioFilePathException {
        AudioFile audioFile = new AudioFile(testAudioFile.getAbsolutePath());
        long frameCount = detector.getFrameCount(audioFile);
        assertTrue(frameCount > 0);
    }

    @Test
    void testDetectFormatWithNullAudioFile() {
        assertThrows(IllegalArgumentException.class, () -> {
            detector.detectFormat((AudioFile) null);
        });
    }

    @Test
    void testDetectFormatWithNullFilePath() {
        assertThrows(IllegalArgumentException.class, () -> {
            detector.detectFormat((String) null);
        });
    }

    @Test
    void testDetectFormatWithEmptyFilePath() {
        assertThrows(IllegalArgumentException.class, () -> {
            detector.detectFormat("");
        });
    }

    @Test
    void testAudioFormatInfoToString() throws IOException, AudioFilePathException {
        AudioFile audioFile = new AudioFile(testAudioFile.getAbsolutePath());
        FmodCore.AudioFormatInfo formatInfo = detector.detectFormat(audioFile);
        
        String toString = formatInfo.toString();
        assertNotNull(toString);
        assertTrue(toString.contains("AudioFormatInfo"));
        assertTrue(toString.contains("channels="));
        assertTrue(toString.contains("bits="));
        assertTrue(toString.contains("rate="));
        assertTrue(toString.contains("frames="));
        assertTrue(toString.contains("duration="));
    }
}
