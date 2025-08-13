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

@DisplayName("LibPennTotalRecall")
class LibPennTotalRecallTest {
    private static LibPennTotalRecall lib;
    private static File testFile;
    private static int actualSampleRate;
    private static long totalFrames;

    @BeforeAll
    static void setup() throws Exception {
        lib = LibPennTotalRecall.instance;
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

            System.out.println("Test file: " + testFile.getName());
            System.out.println("Sample rate: " + actualSampleRate + " Hz");
            System.out.println("Total frames: " + totalFrames);
            System.out.println(
                    "Duration: " + (totalFrames / (double) actualSampleRate) + " seconds");
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
        long endFrame = Math.min(actualSampleRate * 5, totalFrames); // 5 seconds or file length
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
        long firstPos = positions.get(0);
        long lastPos = positions.get(positions.size() - 1);
        long firstTime = timestamps.get(0);
        long lastTime = timestamps.get(timestamps.size() - 1);

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
                Math.min(startFrame + actualSampleRate * 2, totalFrames); // Play for 2 seconds

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
            long expectedPos = (actualSampleRate * 550) / 1000; // ~550ms in frames
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
    @DisplayName("stop returns reasonable position")
    void stopReturnsReasonablePosition() throws InterruptedException {
        long endFrame = Math.min(actualSampleRate * 10, totalFrames);
        int result = lib.startPlayback(testFile.getAbsolutePath(), 0, endFrame);
        assertEquals(0, result, "Playback should start successfully");

        Thread.sleep(500); // Play for 500ms

        long stopPos = lib.stopPlayback();

        // Should have played roughly 500ms worth of frames (±200ms tolerance)
        long expectedFrames = (actualSampleRate * 500) / 1000;
        long tolerance = (actualSampleRate * 200) / 1000;

        assertTrue(
                stopPos > expectedFrames - tolerance && stopPos < expectedFrames + tolerance,
                String.format(
                        "Stop position %d not within expected range [%d, %d]",
                        stopPos, expectedFrames - tolerance, expectedFrames + tolerance));
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
    @DisplayName("multiple start/stop cycles work")
    void multipleStartStopCycles() throws InterruptedException {
        for (int i = 0; i < 5; i++) {
            int result = lib.startPlayback(testFile.getAbsolutePath(), i * 1000, (i + 1) * 5000);
            assertEquals(0, result, "Playback " + i + " should start");

            Thread.sleep(100);
            assertTrue(lib.playbackInProgress(), "Should be playing cycle " + i);

            long pos = lib.streamPosition();
            assertTrue(pos >= 0, "Position should be valid in cycle " + i);

            lib.stopPlayback();
            assertFalse(lib.playbackInProgress(), "Should be stopped after cycle " + i);

            Thread.sleep(50); // Let system settle between cycles
        }
    }
}
