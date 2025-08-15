package audio;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@DisplayName("FmodCore")
@AudioHardware
class FmodCoreTest {
    private static final Logger logger = LoggerFactory.getLogger(FmodCoreTest.class);
    private static FmodCore lib;
    private static File testFile;
    private static int actualSampleRate;
    private static long totalFrames;

    @BeforeAll
    static void setup() throws Exception {
        lib = FmodCore.instance;
        // Use the known sample file with verified properties
        testFile = new File("packaging/samples/sample.wav");
        assertTrue(testFile.exists(), "Test audio file not found: " + testFile.getAbsolutePath());

        // Get actual audio properties
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(testFile)) {
            AudioFormat format = ais.getFormat();
            actualSampleRate = (int) format.getSampleRate();
            totalFrames = ais.getFrameLength();

            // Verify expected format
            assertEquals(44100, actualSampleRate, "Sample rate should be 44100 Hz");
            assertEquals(1, format.getChannels(), "Should be mono");
            assertEquals(16, format.getSampleSizeInBits(), "Should be 16-bit");

            logger.info("Test file: " + testFile.getName());
            logger.info("Sample rate: " + actualSampleRate + " Hz");
            logger.info("Total frames: " + totalFrames);
            logger.info("Duration: " + (totalFrames / (double) actualSampleRate) + " seconds");
        }
    }

    @AfterEach
    void cleanup() throws InterruptedException {
        if (lib.playbackInProgress()) {
            lib.stopPlayback();
        }
        Thread.sleep(100); // Let audio system settle
    }

    @Test
    @DisplayName("position increases over time during playback")
    void positionIncreasesOverTime() throws InterruptedException {
        List<Long> positions = new ArrayList<>();
        List<Long> timestamps = new ArrayList<>();

        // Start playback (use actual sample rate and ensure we don't exceed file length)
        long endFrame = Math.min(actualSampleRate * 5L, totalFrames); // 5 seconds or file length
        int result = lib.startPlayback(testFile.getAbsolutePath(), 0, endFrame);
        assertEquals(0, result, "Playback should start successfully");
        assertTrue(lib.playbackInProgress(), "Should be playing");

        // Sample position every 100ms for 2 seconds
        for (int i = 0; i < 20; i++) {
            long pos = lib.streamPosition();
            long time = System.currentTimeMillis();

            if (pos >= 0) { // Valid position
                positions.add(pos);
                timestamps.add(time);
            }

            Thread.sleep(100);
        }

        lib.stopPlayback();

        // Verify we got samples
        assertTrue(positions.size() >= 10, "Should have collected at least 10 position samples");

        // Check positions are increasing
        for (int i = 1; i < positions.size(); i++) {
            assertTrue(
                    positions.get(i) >= positions.get(i - 1),
                    String.format(
                            "Position went backwards: %d -> %d at sample %d",
                            positions.get(i - 1), positions.get(i), i));
        }

        // Check position roughly matches elapsed time
        // First and last valid positions
        long firstPos = positions.getFirst();
        long lastPos = positions.getLast();
        long firstTime = timestamps.getFirst();
        long lastTime = timestamps.getLast();

        long framesElapsed = lastPos - firstPos;
        long msElapsed = lastTime - firstTime;

        // Convert frames to milliseconds (frames / sample_rate * 1000)
        long expectedMs = (framesElapsed * 1000) / actualSampleRate;

        // Allow 30% tolerance for timing (audio buffering can cause variation)
        double tolerance = 0.3;
        double diff = Math.abs(expectedMs - msElapsed) / (double) Math.max(expectedMs, 1);

        assertTrue(
                diff < tolerance,
                String.format(
                        "Position doesn't match time. Frames: %d, Expected ms: %d, Actual ms: %d"
                                + " (%.1f%% diff)",
                        framesElapsed, expectedMs, msElapsed, diff * 100));
    }

    @Test
    @DisplayName("reports position relative to start frame")
    void reportsPositionRelativeToStartFrame() throws InterruptedException {
        // Start playback at 1 second into the file
        long startFrame = actualSampleRate; // 1 second
        long endFrame =
                Math.min(startFrame + actualSampleRate * 2L, totalFrames); // Play for 2 seconds

        assertTrue(startFrame < totalFrames, "Start frame should be within file");

        int result = lib.startPlayback(testFile.getAbsolutePath(), startFrame, endFrame);
        assertEquals(0, result, "Playback should start successfully");

        Thread.sleep(50); // Give it time to start

        long pos = lib.streamPosition();

        // Position should be RELATIVE to start frame (near 0, not near startFrame)
        assertTrue(
                pos >= 0 && pos < actualSampleRate / 10, // Within first 100ms worth of frames
                String.format("Initial position should be near 0 (relative), was: %d", pos));

        // Play for another 500ms
        Thread.sleep(500);

        if (lib.playbackInProgress()) {
            long laterPos = lib.streamPosition();

            // Position should still be relative (around 550ms worth of frames, not startFrame +
            // 550ms)
            long expectedPos = (actualSampleRate * 550L) / 1000; // ~550ms in frames
            long tolerance = actualSampleRate / 5; // 200ms tolerance

            assertTrue(laterPos > 0, "Position should be positive");
            assertTrue(laterPos < (endFrame - startFrame), "Position should be less than range");
            assertTrue(
                    Math.abs(laterPos - expectedPos) < tolerance,
                    String.format(
                            "Position should be ~%d frames (relative), was: %d",
                            expectedPos, laterPos));
        }

        lib.stopPlayback();
    }

    @Test
    @DisplayName("auto-stops at end frame")
    void autoStopsAtEndFrame() throws InterruptedException {
        long endFrame = actualSampleRate / 10; // 100ms worth

        int result = lib.startPlayback(testFile.getAbsolutePath(), 0, endFrame);
        assertEquals(0, result, "Playback should start successfully");

        Thread.sleep(300); // Wait longer than the playback duration

        assertFalse(lib.playbackInProgress(), "Should have auto-stopped");

        // After auto-stop, streamPosition returns -1 (not playing)
        long pos = lib.streamPosition();
        assertEquals(-1, pos, "Position should be -1 after auto-stop");
    }

    @Test
    @DisplayName("invalid file path returns error")
    void invalidFilePathReturnsError() {
        int result = lib.startPlayback("/nonexistent/file.wav", 0, 1000);
        assertEquals(-3, result, "Should return -3 for unable to find or use file");
        assertFalse(lib.playbackInProgress(), "Should not be playing");
    }

    @Test
    @DisplayName("stream position when not playing")
    void streamPositionWhenNotPlaying() {
        assertFalse(lib.playbackInProgress(), "Should not be playing initially");
        long pos = lib.streamPosition();
        assertEquals(-1, pos, "Position should be -1 when not playing");
    }

    @Test
    @DisplayName("double start playback returns error")
    void doubleStartPlaybackReturnsError() throws InterruptedException {
        int result1 = lib.startPlayback(testFile.getAbsolutePath(), 0, actualSampleRate);
        assertEquals(0, result1, "First playback should start successfully");
        assertTrue(lib.playbackInProgress(), "Should be playing");

        int result2 = lib.startPlayback(testFile.getAbsolutePath(), 0, actualSampleRate);
        assertEquals(-4, result2, "Second playback should return -4 for inconsistent state");
        assertTrue(lib.playbackInProgress(), "Original playback should continue");

        lib.stopPlayback();
    }

    @Test
    @DisplayName("playback from middle to file end")
    void playbackFromMiddleToEnd() throws InterruptedException {
        long startFrame = totalFrames / 2; // Start halfway through
        int result = lib.startPlayback(testFile.getAbsolutePath(), startFrame, totalFrames);
        assertEquals(0, result, "Should handle mid-file to end playback");

        Thread.sleep(100);
        long pos = lib.streamPosition();
        assertTrue(pos >= 0 && pos < (totalFrames - startFrame), "Position should be within range");

        lib.stopPlayback();
    }

    @Test
    @DisplayName("stream position bounds check")
    void streamPositionBoundsCheck() throws InterruptedException {
        long startFrame = 1000;
        long endFrame = startFrame + actualSampleRate; // 1 second range

        int result = lib.startPlayback(testFile.getAbsolutePath(), startFrame, endFrame);
        assertEquals(0, result, "Playback should start successfully");

        // Verify position stays within bounds over time
        boolean foundValidPosition = false;
        for (int i = 0; i < 10; i++) {
            long pos = lib.streamPosition();
            if (pos >= 0) { // Valid position
                foundValidPosition = true;
                assertTrue(
                        pos < (endFrame - startFrame),
                        "Position should never exceed range: "
                                + pos
                                + " >= "
                                + (endFrame - startFrame));
            }
            Thread.sleep(50);
        }

        assertTrue(foundValidPosition, "Should have found at least one valid position");
        lib.stopPlayback();
    }

    @Test
    @DisplayName("precise seek accuracy")
    void preciseSeekAccuracy() throws InterruptedException {
        // Test multiple seek points across the file
        long[] seekTargets = {
            0, // Start
            actualSampleRate, // 1 second
            actualSampleRate * 5, // 5 seconds
            totalFrames / 2, // Middle
            totalFrames - actualSampleRate // Near end
        };

        for (long targetFrame : seekTargets) {
            if (targetFrame >= totalFrames) continue;

            // Start playback at exact target
            long endFrame = Math.min(targetFrame + actualSampleRate, totalFrames);
            int result = lib.startPlayback(testFile.getAbsolutePath(), targetFrame, endFrame);
            assertEquals(0, result, "Should start at frame " + targetFrame);

            Thread.sleep(50); // Let it stabilize

            long actualPosition = lib.streamPosition(); // Relative to targetFrame
            long absolutePosition = targetFrame + actualPosition;

            // Log the seek accuracy for analysis
            long error = Math.abs(absolutePosition - targetFrame);
            double errorMs = (error * 1000.0) / actualSampleRate;

            logger.info(
                    String.format(
                            "Seek to %d: actual %d, error %d frames (%.1fms)",
                            targetFrame, absolutePosition, error, errorMs));

            // Use more generous tolerance - audio systems have inherent latency
            long tolerance = actualSampleRate / 10; // 100ms worth of frames

            assertTrue(
                    error <= tolerance,
                    String.format(
                            "Seek error too large. Target: %d, Actual: %d, Error: %d frames"
                                    + " (%.1fms)",
                            targetFrame, absolutePosition, error, errorMs));

            lib.stopPlayback();
            Thread.sleep(50); // Let system settle between seeks
        }
    }
}
