package audio;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Test suite to analyze FMOD timing behavior and identify issues with playhead jittering.
 *
 * <p>This test captures detailed timing data during the critical startup phase to help debug
 * differences between the original FMOD Ex implementation and our FMOD Core JNA version.
 */
public class FMODTimingTest {

    private LibPennTotalRecall lib;
    private static final String TEST_AUDIO_FILE = "deploy/all/sample.wav";

    @BeforeEach
    void setUp() {
        lib = LibPennTotalRecall.instance;
    }

    @AfterEach
    void tearDown() {
        // Ensure cleanup after each test
        try {
            lib.stopPlayback();
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    @Test
    @DisplayName("Basic FMOD initialization test")
    void testFMODInitialization() {
        File audioFile = new File(TEST_AUDIO_FILE);
        if (!audioFile.exists()) {
            System.out.println("Warning: Test audio file not found at " + TEST_AUDIO_FILE);
            System.out.println("Skipping FMOD timing tests");
            return;
        }

        // Test basic playback startup
        int result = lib.startPlayback(audioFile.getAbsolutePath(), 0, 44100);
        assertTrue(result >= 0, "FMOD should initialize successfully. Error code: " + result);

        // Basic functionality check
        assertTrue(lib.playbackInProgress(), "Playback should be in progress after start");

        lib.stopPlayback();
        assertFalse(lib.playbackInProgress(), "Playback should stop after stopPlayback()");
    }

    @Test
    @DisplayName("Startup timing behavior analysis")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testStartupTimingBehavior() {
        File audioFile = new File(TEST_AUDIO_FILE);
        if (!audioFile.exists()) {
            System.out.println("Warning: Test audio file not found, skipping timing test");
            return;
        }

        long startFrame = 0;
        long endFrame = 44100 * 2; // 2 seconds

        System.out.println("\n=== FMOD Startup Timing Analysis ===");
        System.out.println("File: " + audioFile.getAbsolutePath());

        // Start playback
        int result = lib.startPlayback(audioFile.getAbsolutePath(), startFrame, endFrame);
        assertEquals(0, result, "Playback should start successfully");

        // Capture timing data during critical first 200ms
        List<TimingSample> samples = new ArrayList<>();
        long testStart = System.nanoTime();

        for (int i = 0; i < 20; i++) { // 20 samples over ~200ms
            long currentTime = System.nanoTime();
            long elapsedMs = (currentTime - testStart) / 1_000_000;

            long position = lib.streamPosition();
            boolean playing = lib.playbackInProgress();

            samples.add(new TimingSample(elapsedMs, position, playing));

            try {
                Thread.sleep(10); // Sample every 10ms
            } catch (InterruptedException e) {
                break;
            }
        }

        lib.stopPlayback();

        // Analyze the timing data
        analyzeTimingBehavior(samples);
    }

    @Test
    @DisplayName("Position consistency during stable playback")
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void testPositionConsistency() {
        File audioFile = new File(TEST_AUDIO_FILE);
        if (!audioFile.exists()) {
            System.out.println("Warning: Test audio file not found, skipping consistency test");
            return;
        }

        long startFrame = 44100; // Start 1 second in to skip startup issues
        long endFrame = 44100 * 5; // 5 seconds total

        int result = lib.startPlayback(audioFile.getAbsolutePath(), startFrame, endFrame);
        assertEquals(0, result, "Playback should start successfully");

        // Wait for initial stabilization
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Sample during stable playback
        List<Long> positions = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            long pos = lib.streamPosition();
            positions.add(pos);

            try {
                Thread.sleep(50); // 50ms between samples
            } catch (InterruptedException e) {
                break;
            }
        }

        lib.stopPlayback();

        // Verify positions are monotonically increasing (allowing for small timing variations)
        for (int i = 1; i < positions.size(); i++) {
            long prev = positions.get(i - 1);
            long curr = positions.get(i);

            if (prev > 0 && curr > 0) { // Only check valid positions
                assertTrue(
                        curr >= prev,
                        String.format(
                                "Position should not decrease: %d -> %d at sample %d",
                                prev, curr, i));
            }
        }

        System.out.println("\nPosition consistency test - samples: " + positions);
    }

    private void analyzeTimingBehavior(List<TimingSample> samples) {
        System.out.println("\nTime(ms)\tPosition\tPlaying\tDelta\tNotes");
        System.out.println("-------\t--------\t-------\t-----\t-----");

        long previousPosition = -1;
        int negativeCount = 0;
        int zeroCount = 0;
        int backwardJumps = 0;
        long firstValidTime = -1;
        long firstValidPosition = -1;

        for (int i = 0; i < samples.size(); i++) {
            TimingSample sample = samples.get(i);
            long delta = previousPosition >= 0 ? sample.position - previousPosition : 0;
            String notes = "";

            if (sample.position < 0) {
                negativeCount++;
                notes += "NEGATIVE ";
            } else if (sample.position == 0) {
                zeroCount++;
                notes += "ZERO ";
            } else if (firstValidPosition < 0) {
                firstValidPosition = sample.position;
                firstValidTime = sample.timeMs;
                notes += "FIRST_VALID ";
            }

            if (previousPosition > 0 && sample.position > 0 && delta < 0) {
                backwardJumps++;
                notes += "BACKWARD_JUMP ";
            }

            if (Math.abs(delta) > 2000) { // Large jump
                notes += "LARGE_JUMP ";
            }

            System.out.printf(
                    "%7d\t%8d\t%7s\t%5d\t%s%n",
                    sample.timeMs, sample.position, sample.playing, delta, notes.trim());

            previousPosition = sample.position;
        }

        // Summary analysis
        System.out.println("\n=== Analysis Summary ===");
        System.out.printf("Total samples: %d%n", samples.size());
        System.out.printf(
                "Negative positions: %d (%.1f%%)%n",
                negativeCount, 100.0 * negativeCount / samples.size());
        System.out.printf(
                "Zero positions: %d (%.1f%%)%n", zeroCount, 100.0 * zeroCount / samples.size());
        System.out.printf("Backward jumps: %d%n", backwardJumps);
        System.out.printf("First valid position: %d at %dms%n", firstValidPosition, firstValidTime);

        // Assertions for test validation
        assertTrue(firstValidTime >= 0, "Should eventually get valid position data");
        assertTrue(
                backwardJumps <= 2,
                "Should have minimal backward jumps (got " + backwardJumps + ")");

        if (firstValidTime > 100) {
            System.out.println("WARNING: Long startup latency detected (" + firstValidTime + "ms)");
        }
    }

    static class TimingSample {
        final long timeMs;
        final long position;
        final boolean playing;

        TimingSample(long timeMs, long position, boolean playing) {
            this.timeMs = timeMs;
            this.position = position;
            this.playing = playing;
        }
    }
}
