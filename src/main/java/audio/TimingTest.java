package audio;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Test to capture FMOD timing behavior during initial playback phase. This will help identify
 * differences between FMOD Ex and FMOD Core implementations.
 */
public class TimingTest {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java TimingTest <audio_file_path>");
            System.exit(1);
        }

        String audioFile = args[0];
        if (!new File(audioFile).exists()) {
            System.err.println("Audio file not found: " + audioFile);
            System.exit(1);
        }

        LibPennTotalRecall lib = LibPennTotalRecall.instance;

        // Test parameters
        long startFrame = 0;
        long endFrame = 44100 * 5; // 5 seconds at 44.1kHz

        System.out.println("=== FMOD Timing Test ===");
        System.out.println("File: " + audioFile);
        System.out.println("Start frame: " + startFrame);
        System.out.println("End frame: " + endFrame);
        System.out.println();

        // Start playback
        int result = lib.startPlayback(audioFile, startFrame, endFrame);
        if (result != 0) {
            System.err.println("Failed to start playback. Error code: " + result);
            System.exit(1);
        }

        // Capture position data during first 500ms
        List<TimingData> timingData = new ArrayList<>();
        long testStartTime = System.nanoTime();

        for (int i = 0; i < 50; i++) { // 50 samples over ~500ms
            long currentTime = System.nanoTime();
            long elapsedMs = (currentTime - testStartTime) / 1_000_000;

            long position = lib.streamPosition();
            boolean inProgress = lib.playbackInProgress();

            timingData.add(new TimingData(elapsedMs, position, inProgress));

            try {
                Thread.sleep(10); // Sample every 10ms
            } catch (InterruptedException e) {
                break;
            }
        }

        // Stop playback
        lib.stopPlayback();

        // Analyze results
        System.out.println("Time(ms)\tPosition\tPlaying\tDelta\tNotes");
        System.out.println("-------\t--------\t-------\t-----\t-----");

        long previousPosition = -1;
        int negativeCount = 0;
        int zeroCount = 0;
        int jumpCount = 0;
        long firstValidPosition = -1;
        long firstValidTime = -1;

        for (TimingData data : timingData) {
            long delta = previousPosition >= 0 ? data.position - previousPosition : 0;
            String notes = "";

            if (data.position < 0) {
                negativeCount++;
                notes = "NEGATIVE";
            } else if (data.position == 0) {
                zeroCount++;
                notes = "ZERO";
            } else if (firstValidPosition < 0) {
                firstValidPosition = data.position;
                firstValidTime = data.timeMs;
                notes = "FIRST_VALID";
            } else if (Math.abs(delta) > 1000) { // Jump > 1000 frames
                jumpCount++;
                notes = "LARGE_JUMP";
            }

            System.out.printf(
                    "%7d\t%8d\t%7s\t%5d\t%s%n",
                    data.timeMs, data.position, data.playing, delta, notes);

            previousPosition = data.position;
        }

        System.out.println();
        System.out.println("=== Analysis ===");
        System.out.printf("Negative positions: %d%n", negativeCount);
        System.out.printf("Zero positions: %d%n", zeroCount);
        System.out.printf("Large jumps: %d%n", jumpCount);
        System.out.printf("First valid position: %d at %dms%n", firstValidPosition, firstValidTime);

        // Calculate stability metrics
        if (firstValidTime > 0) {
            System.out.printf("Startup latency: %dms%n", firstValidTime);
        }

        // Look for consistent advancement after startup
        int consistentAdvancement = 0;
        for (int i = 1; i < timingData.size(); i++) {
            TimingData curr = timingData.get(i);
            TimingData prev = timingData.get(i - 1);

            if (curr.position > 0 && prev.position > 0 && curr.position > prev.position) {
                consistentAdvancement++;
            }
        }

        System.out.printf(
                "Consistent advancement samples: %d/%d%n",
                consistentAdvancement, timingData.size() - 1);
    }

    static class TimingData {
        final long timeMs;
        final long position;
        final boolean playing;

        TimingData(long timeMs, long position, boolean playing) {
            this.timeMs = timeMs;
            this.position = position;
            this.playing = playing;
        }
    }
}
