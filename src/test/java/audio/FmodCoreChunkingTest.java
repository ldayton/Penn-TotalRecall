package audio;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@DisplayName("FmodCore Waveform Chunking & Playback Integration")
@AudioEngine
class FmodCoreChunkingTest {
    private static final Logger logger = LoggerFactory.getLogger(FmodCoreChunkingTest.class);

    private static FmodCore lib;
    private static File sampleFile;
    private static int sampleRate;
    private static long totalFrames;

    @BeforeAll
    static void setup() throws Exception {
        // Create library with the same wiring as other audio-engine tests
        env.Platform platform = new env.Platform();
        env.AppConfig appConfig = new env.AppConfig();
        AudioSystemManager audioManager = new AudioSystemManager(appConfig, platform);
        lib = new FmodCore(audioManager);

        sampleFile = new File("packaging/samples/sample.wav");
        assertTrue(sampleFile.exists(), "sample.wav not found: " + sampleFile.getAbsolutePath());

        var info = lib.detectAudioFormat(sampleFile.getAbsolutePath());
        sampleRate = info.getSampleRate();
        totalFrames = info.getFrameLength();
        logger.info(
                "sample.wav: rate={}Hz frames={} dur={}s",
                sampleRate,
                totalFrames,
                totalFrames / (double) sampleRate);
    }

    @Test
    @DisplayName("readAudioChunk: second chunk differs from first")
    void readAudioChunk_secondChunkDiffersFromFirst() throws Exception {
        // Match Waveform.renderChunkDirectConfigured constants
        double chunkSeconds = 10.0;
        double overlapSeconds = 0.25;

        var first =
                lib.readAudioChunk(sampleFile.getAbsolutePath(), 0, chunkSeconds, overlapSeconds);
        var second =
                lib.readAudioChunk(sampleFile.getAbsolutePath(), 1, chunkSeconds, overlapSeconds);

        assertNotNull(first);
        assertNotNull(second);
        assertTrue(first.samples.length > 0 && second.samples.length > 0);

        // Compare overlap region excluded to reduce similarity impact
        int minFrames = Math.min(first.samples.length, second.samples.length);
        assertTrue(minFrames > sampleRate, "chunk too small to compare meaningfully");

        // Compute simple normalized correlation over the middle region
        int start = Math.min(minFrames / 10, 2_000);
        int end = minFrames - start;
        double meanA = 0, meanB = 0;
        int n = end - start;
        for (int i = start; i < end; i++) {
            meanA += first.samples[i];
            meanB += second.samples[i];
        }
        meanA /= n;
        meanB /= n;
        double num = 0, denA = 0, denB = 0;
        for (int i = start; i < end; i++) {
            double a = first.samples[i] - meanA;
            double b = second.samples[i] - meanB;
            num += a * b;
            denA += a * a;
            denB += b * b;
        }
        double corr = num / Math.max(1e-12, Math.sqrt(denA * denB));
        logger.info("Chunk correlation (0 vs 1): {}", corr);

        // Expect noticeable difference (< 0.98) for typical speech/music; adjust as needed
        assertTrue(
                corr < 0.98, "Chunks look too similar; seek may be incorrect (corr=" + corr + ")");
    }

    @Test
    @DisplayName("playback continues while reading waveform chunks")
    void playbackContinuesWhileReadingChunks() throws Exception {
        // Start long-ish playback
        long end = Math.min(totalFrames, sampleRate * 22L);
        int res = lib.startPlayback(sampleFile.getAbsolutePath(), 0, end);
        assertEquals(0, res, "playback should start");
        assertTrue(lib.playbackInProgress());

        // Kick off a concurrent chunk read (10s chunk #1) that used to disrupt playback
        Thread reader =
                new Thread(
                        () -> {
                            try {
                                lib.readAudioChunk(sampleFile.getAbsolutePath(), 1, 10.0, 0.25);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        },
                        "WaveformChunkReader");
        reader.start();

        // Observe progress for ~2 seconds while chunk read runs
        long first = -1, last = -1;
        int ok = 0;
        long startMs = System.currentTimeMillis();
        while (System.currentTimeMillis() - startMs < 2000) {
            long p = lib.streamPosition();
            if (p >= 0) {
                ok++;
                if (first < 0) first = p;
                last = p;
            }
            Thread.sleep(50);
        }

        reader.join(2000);
        assertFalse(reader.isAlive(), "chunk reader did not complete");
        assertTrue(lib.playbackInProgress(), "playback stopped unexpectedly during chunk read");

        assertTrue(ok > 10, "insufficient samples collected");
        assertTrue(last > first, "position did not advance");
        assertTrue(last >= sampleRate / 2, "position advanced too little: " + last);

        // Cleanup
        lib.stopPlayback();
    }
}
