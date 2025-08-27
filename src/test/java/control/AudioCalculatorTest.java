package control;

import components.audiofiles.AudioFile;
import components.audiofiles.AudioFile.AudioFilePathException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;

/**
 * Tests for the refactored AudioCalculator that uses FMOD for format detection.
 */
class AudioCalculatorTest {

    private static File testAudioFile;

    @BeforeAll
    static void setup() {
        // Use the known sample file
        testAudioFile = new File("packaging/samples/sample.wav");
        assertTrue(testAudioFile.exists(), "Test audio file not found: " + testAudioFile.getAbsolutePath());
    }

    @Test
    void testAudioCalculatorConstructor() throws Exception {
        AudioFile audioFile = new AudioFile(testAudioFile.getAbsolutePath());
        AudioCalculator calculator = new AudioCalculator(audioFile);

        assertNotNull(calculator);
        assertTrue(calculator.durationInSeconds() > 0);
        assertTrue(calculator.durationInFrames() > 0);
        assertTrue(calculator.numChannels() > 0);
        assertTrue(calculator.sampleSizeInBits() > 0);
        assertTrue(calculator.frameRate() > 0);
        assertEquals(audioFile, calculator.getAudioFile());
    }

    @Test
    void testAudioCalculatorWithNonExistentFile() throws AudioFilePathException {
        AudioFile nonExistentFile = new AudioFile("/path/to/nonexistent/file.wav");
        assertThrows(IOException.class, () -> {
            new AudioCalculator(nonExistentFile);
        });
    }

    @Test
    void testFrameCalculations() throws Exception {
        AudioFile audioFile = new AudioFile(testAudioFile.getAbsolutePath());
        AudioCalculator calculator = new AudioCalculator(audioFile);

        // Test millis to frames conversion
        long frames = calculator.millisToFrames(1000); // 1 second
        assertTrue(frames > 0);
        assertEquals(calculator.sampleRate(), frames, 1); // Should be approximately sample rate

        // Test seconds to frames conversion
        long framesFromSeconds = calculator.secondsToFrames(1.0);
        assertTrue(framesFromSeconds > 0);
        assertEquals(calculator.sampleRate(), framesFromSeconds, 1);

        // Test frames to millis conversion
        double millis = calculator.framesToMillis((long) calculator.sampleRate());
        assertEquals(1000.0, millis, 10.0); // Should be approximately 1000ms

        // Test frames to seconds conversion
        double seconds = calculator.framesToSec((long) calculator.sampleRate());
        assertEquals(1.0, seconds, 0.01); // Should be approximately 1 second
    }

    @Test
    void testByteCalculations() throws Exception {
        AudioFile audioFile = new AudioFile(testAudioFile.getAbsolutePath());
        AudioCalculator calculator = new AudioCalculator(audioFile);

        // Test frame size calculations
        assertTrue(calculator.frameSizeInBits() > 0);
        assertTrue(calculator.frameSizeInBytes() > 0);
        assertTrue(calculator.sampleSizeInBytes() > 0);

        // Test frames to bytes conversion
        long bytes = calculator.framesToBytes(1);
        assertTrue(bytes > 0);
        assertEquals(calculator.frameSizeInBytes(), bytes);
    }

    @Test
    void testNanosToFrames() throws Exception {
        AudioFile audioFile = new AudioFile(testAudioFile.getAbsolutePath());
        AudioCalculator calculator = new AudioCalculator(audioFile);

        // Test nanoseconds to frames conversion
        long frames = calculator.nanosToFrames(1_000_000_000); // 1 second in nanoseconds
        assertTrue(frames > 0);
        assertEquals(calculator.sampleRate(), frames, 1); // Should be approximately sample rate
    }
}
