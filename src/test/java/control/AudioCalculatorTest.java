package control;

import static org.junit.jupiter.api.Assertions.*;

import components.audiofiles.AudioFile;
import components.audiofiles.AudioFile.AudioFilePathException;
import java.io.File;
import java.io.IOException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Tests for the refactored AudioCalculator that uses FMOD for format detection. */
class AudioCalculatorTest {

    private static File testAudioFile;

    @BeforeAll
    static void setup() {
        // Use the known sample file
        testAudioFile = new File("packaging/samples/sample.wav");
        assertTrue(
                testAudioFile.exists(),
                "Test audio file not found: " + testAudioFile.getAbsolutePath());
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
    void testSampleFileIs44100Hz() throws Exception {
        AudioFile audioFile = new AudioFile(testAudioFile.getAbsolutePath());
        AudioCalculator calculator = new AudioCalculator(audioFile);

        double sampleRate = calculator.sampleRate();
        double frameRate = calculator.frameRate();

        System.out.println("Detected sample rate: " + sampleRate);
        System.out.println("Detected frame rate: " + frameRate);

        // The sample.wav file is known to be 44.1kHz
        assertEquals(
                44100.0, sampleRate, 1.0, "Sample rate should be 44100 Hz, got: " + sampleRate);
        assertEquals(44100.0, frameRate, 1.0, "Frame rate should be 44100 Hz, got: " + frameRate);

        // Frame rate should equal sample rate for uncompressed audio
        assertEquals(sampleRate, frameRate, 0.001, "Frame rate should equal sample rate");
    }

    @Test
    void testWaveformBufferArraySizeDoesNotOverflow() throws Exception {
        AudioFile audioFile = new AudioFile(testAudioFile.getAbsolutePath());
        AudioCalculator calculator = new AudioCalculator(audioFile);

        double frameRate = calculator.frameRate();
        int chunkSizeSeconds = 10; // Same as WaveformBuffer.CHUNK_SIZE_SECONDS
        double preDataSeconds = 0.25; // Same as WaveformBuffer preDataSeconds

        System.out.println("Frame rate for array calculation: " + frameRate);

        // This is the exact calculation that WaveformBuffer does in getValsToDraw()
        long samplesArraySize = (long) (frameRate * chunkSizeSeconds);
        long preDataSizeInFrames = (long) (frameRate * preDataSeconds);
        long totalArraySize = samplesArraySize + preDataSizeInFrames;

        System.out.println("WaveformBuffer would allocate array of size: " + totalArraySize);

        // JVM array size limit is typically Integer.MAX_VALUE - 2 to Integer.MAX_VALUE - 8
        long maxArraySize = Integer.MAX_VALUE - 8;

        assertTrue(totalArraySize > 0, "Array size should be positive: " + totalArraySize);
        assertTrue(
                totalArraySize <= maxArraySize,
                String.format(
                        "Array size %d exceeds JVM limit %d (frameRate=%.0f)",
                        totalArraySize, maxArraySize, frameRate));

        // Also verify the array can actually be cast to int without overflow
        assertTrue(
                totalArraySize <= Integer.MAX_VALUE,
                "Array size must fit in int: " + totalArraySize);
    }

    @Test
    void testAudioCalculatorWithNonExistentFile() throws AudioFilePathException {
        AudioFile nonExistentFile = new AudioFile("/path/to/nonexistent/file.wav");
        assertThrows(
                IOException.class,
                () -> {
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
