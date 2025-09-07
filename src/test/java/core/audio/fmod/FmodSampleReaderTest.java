package core.audio.fmod;

import static org.junit.jupiter.api.Assertions.*;

import annotations.Audio;
import core.audio.AudioData;
import core.audio.AudioMetadata;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;

/** Tests for FmodSimpleSampleReader that loads entire files into memory. */
@Audio
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FmodSampleReaderTest {

    private FmodSampleReader reader;
    private FmodLibraryLoader libraryLoader;

    // Test audio files
    private static final Path SAMPLE_WAV = Paths.get("packaging/samples/sample.wav");
    private static final Path SWEEP_WAV = Paths.get("packaging/samples/sweep.wav");

    // Known properties of sample.wav (mono, 44100Hz, 16-bit)
    private static final int SAMPLE_WAV_RATE = 44100;
    private static final int SAMPLE_WAV_CHANNELS = 1;
    private static final int SAMPLE_WAV_BITS = 16;
    private static final long SAMPLE_WAV_FRAMES = 1993624; // ~45.2 seconds

    @BeforeEach
    void setUp() {
        libraryLoader = new FmodLibraryLoader(new core.env.AppConfig(), new core.env.Platform());
        reader = new FmodSampleReader(libraryLoader);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (reader != null) {
            reader.close();
        }
    }

    @Test
    void testGetMetadata() throws Exception {
        AudioMetadata metadata = reader.getMetadata(SAMPLE_WAV).get(5, TimeUnit.SECONDS);

        assertEquals(SAMPLE_WAV_RATE, metadata.sampleRate());
        assertEquals(SAMPLE_WAV_CHANNELS, metadata.channelCount());
        assertEquals(SAMPLE_WAV_BITS, metadata.bitsPerSample());
        assertEquals(SAMPLE_WAV_FRAMES, metadata.frameCount());
    }

    @Test
    void testReadSamplesInValidRange() throws Exception {
        // Read 1000 frames from middle of file
        long startFrame = 10000;
        long frameCount = 1000;

        AudioData data =
                reader.readSamples(SAMPLE_WAV, startFrame, frameCount).get(5, TimeUnit.SECONDS);

        assertEquals(startFrame, data.startFrame());
        assertEquals(frameCount, data.frameCount());
        assertEquals(SAMPLE_WAV_RATE, data.sampleRate());
        assertEquals(SAMPLE_WAV_CHANNELS, data.channelCount());
        // Mono: 1000 frames * 1 channel = 1000 samples
        assertEquals(1000, data.samples().length);
    }

    @Test
    void testCachingWorks() throws Exception {
        // First read should load the file
        long start = System.currentTimeMillis();
        AudioData data1 = reader.readSamples(SAMPLE_WAV, 0, 100).get(5, TimeUnit.SECONDS);
        long firstTime = System.currentTimeMillis() - start;

        // Second read should be much faster (from cache)
        start = System.currentTimeMillis();
        AudioData data2 = reader.readSamples(SAMPLE_WAV, 1000, 100).get(5, TimeUnit.SECONDS);
        long secondTime = System.currentTimeMillis() - start;

        // Cache hit should be faster
        assertTrue(secondTime <= firstTime, "Second read should be faster due to caching");

        // Both should have correct data
        assertEquals(100, data1.frameCount());
        assertEquals(100, data2.frameCount());
    }

    @Test
    void testReadPastEof() throws Exception {
        // Try to read past end
        long startFrame = SAMPLE_WAV_FRAMES - 50;
        long requestedFrames = 100;

        AudioData data =
                reader.readSamples(SAMPLE_WAV, startFrame, requestedFrames)
                        .get(5, TimeUnit.SECONDS);

        // Should only get 50 frames (what's available)
        assertEquals(startFrame, data.startFrame());
        assertEquals(50, data.frameCount());
        assertEquals(50 * SAMPLE_WAV_CHANNELS, data.samples().length);
    }

    @Test
    void testReadBeyondEof() throws Exception {
        // Start reading past EOF
        AudioData data =
                reader.readSamples(SAMPLE_WAV, SAMPLE_WAV_FRAMES + 100, 100)
                        .get(5, TimeUnit.SECONDS);

        // Should return empty
        assertEquals(0, data.frameCount());
        assertEquals(0, data.samples().length);
    }

    @Test
    void testMultipleFilesInCache() throws Exception {
        // Load both test files
        AudioData data1 = reader.readSamples(SAMPLE_WAV, 0, 100).get(5, TimeUnit.SECONDS);
        AudioData data2 = reader.readSamples(SWEEP_WAV, 0, 100).get(5, TimeUnit.SECONDS);

        // Both should work
        assertEquals(100, data1.frameCount());
        assertEquals(100, data2.frameCount());

        // Read again from both (should be cached)
        data1 = reader.readSamples(SAMPLE_WAV, 1000, 100).get(5, TimeUnit.SECONDS);
        data2 = reader.readSamples(SWEEP_WAV, 1000, 100).get(5, TimeUnit.SECONDS);

        assertEquals(100, data1.frameCount());
        assertEquals(100, data2.frameCount());
    }

    @Test
    void testCloseClearsCache() throws Exception {
        // Load a file
        AudioData data = reader.readSamples(SAMPLE_WAV, 0, 100).get(5, TimeUnit.SECONDS);
        assertEquals(100, data.frameCount());

        // Close the reader
        reader.close();

        // Should not be able to read after close
        var future = reader.readSamples(SAMPLE_WAV, 0, 100);
        assertThrows(Exception.class, () -> future.get(1, TimeUnit.SECONDS));
    }
}
