package a2.fmod;

import static org.junit.jupiter.api.Assertions.*;

import a2.*;
import a2.SampleReader.ReadRequest;
import annotations.Audio;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/**
 * Comprehensive tests for FmodParallelSampleReader including correctness, performance, thread
 * safety, and resource management.
 */
@Audio
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FmodParallelSampleReaderTest {

    private FmodParallelSampleReader reader;

    @TempDir Path tempDir;

    // Test audio files
    private static final Path SAMPLE_WAV = Paths.get("packaging/samples/sample.wav");
    private static final Path SWEEP_WAV = Paths.get("packaging/samples/sweep.wav");

    // Known properties of sample.wav (mono, 44100Hz, 16-bit)
    private static final int SAMPLE_WAV_RATE = 44100;
    private static final int SAMPLE_WAV_CHANNELS = 1;
    private static final int SAMPLE_WAV_BITS = 16;
    private static final int SAMPLE_WAV_FRAMES = 1993624; // ~45.2 seconds

    private FmodLibraryLoader libraryLoader;

    @BeforeEach
    void setUp() {
        // Create library loader for tests
        libraryLoader =
                new FmodLibraryLoader(
                        new env.AppConfig(), // Use default config
                        new env.Platform() // Default platform detection
                        );
        // Create reader with default parallelism for most tests
        reader =
                new FmodParallelSampleReader(
                        libraryLoader, Runtime.getRuntime().availableProcessors());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (reader != null) {
            reader.close();
        }
    }

    // ========== Core Functionality Tests ==========

    @Test
    void testReadSamplesInValidRange() throws Exception {
        CompletableFuture<AudioData> future = reader.readSamples(SAMPLE_WAV, 0, 44100);
        AudioData data = future.get(5, TimeUnit.SECONDS);

        assertNotNull(data);
        assertNotNull(data.samples());
        assertEquals(SAMPLE_WAV_RATE, data.sampleRate());
        assertEquals(SAMPLE_WAV_CHANNELS, data.channelCount());
        assertEquals(0, data.startFrame());
        assertEquals(44100, data.frameCount());

        // Verify all samples are in valid range [-1.0, 1.0]
        double[] samples = data.samples();
        assertEquals(44100, samples.length); // mono: 1 sample per frame

        for (int i = 0; i < samples.length; i++) {
            double sample = samples[i];
            assertTrue(
                    sample >= -1.0 && sample <= 1.0,
                    "Sample at index " + i + " out of range: " + sample);
        }
    }

    @Test
    void testPcmConversionAccuracy() throws Exception {
        // Test 16-bit PCM conversion
        AudioData data = reader.readSamples(SAMPLE_WAV, 1000, 100).get(5, TimeUnit.SECONDS);

        double[] samples = data.samples();
        assertEquals(100, samples.length);

        // Should have varied samples (not all zero)
        boolean hasPositive = false;
        boolean hasNegative = false;
        for (double sample : samples) {
            if (sample > 0) hasPositive = true;
            if (sample < 0) hasNegative = true;
        }
        assertTrue(hasPositive || hasNegative, "Should have non-zero samples");

        // Test normalization range
        OptionalDouble max = Arrays.stream(samples).max();
        OptionalDouble min = Arrays.stream(samples).min();
        assertTrue(max.isPresent() && max.getAsDouble() <= 1.0);
        assertTrue(min.isPresent() && min.getAsDouble() >= -1.0);
    }

    @Test
    void testReadPastEofReturnsTruncated() throws Exception {
        // Try to read past end
        long startFrame = SAMPLE_WAV_FRAMES - 100;
        long requestedFrames = 200; // Request more than available

        AudioData data =
                reader.readSamples(SAMPLE_WAV, startFrame, requestedFrames)
                        .get(5, TimeUnit.SECONDS);

        assertEquals(100, data.frameCount(), "Should only return available frames");
        assertEquals(100, data.samples().length, "Sample array should match frame count");
        assertEquals(startFrame, data.startFrame());
    }

    @Test
    void testReadBeyondEofReturnsEmpty() throws Exception {
        // Start reading way past the end of file
        AudioData data = reader.readSamples(SAMPLE_WAV, 10000000, 1000).get(5, TimeUnit.SECONDS);

        assertNotNull(data);
        assertEquals(0, data.frameCount());
        assertEquals(0, data.samples().length);
    }

    @Test
    void testInvalidInputsReturnFailedFutures() {
        // Negative start frame
        CompletableFuture<AudioData> future1 = reader.readSamples(SAMPLE_WAV, -1, 100);
        assertThrows(ExecutionException.class, () -> future1.get(1, TimeUnit.SECONDS));

        // Negative frame count
        CompletableFuture<AudioData> future2 = reader.readSamples(SAMPLE_WAV, 0, -100);
        assertThrows(ExecutionException.class, () -> future2.get(1, TimeUnit.SECONDS));

        // Null file path
        assertThrows(NullPointerException.class, () -> reader.readSamples(null, 0, 100));

        // Non-existent file
        Path nonExistent = Paths.get("/does/not/exist.wav");
        CompletableFuture<AudioData> future3 = reader.readSamples(nonExistent, 0, 100);
        assertThrows(ExecutionException.class, () -> future3.get(1, TimeUnit.SECONDS));
    }

    @Test
    void testGetMetadata() throws Exception {
        AudioMetadata metadata = reader.getMetadata(SAMPLE_WAV).get(5, TimeUnit.SECONDS);

        assertNotNull(metadata);
        assertEquals(SAMPLE_WAV_RATE, metadata.sampleRate());
        assertEquals(SAMPLE_WAV_CHANNELS, metadata.channelCount());
        assertEquals(SAMPLE_WAV_BITS, metadata.bitsPerSample());
        assertEquals(SAMPLE_WAV_FRAMES, metadata.frameCount());
    }

    // ========== Consistency Tests ==========

    @Test
    void testConsecutiveReadsConsistent() throws Exception {
        // Read the same range twice
        CompletableFuture<AudioData> future1 = reader.readSamples(SAMPLE_WAV, 5000, 1000);
        CompletableFuture<AudioData> future2 = reader.readSamples(SAMPLE_WAV, 5000, 1000);

        AudioData data1 = future1.get(5, TimeUnit.SECONDS);
        AudioData data2 = future2.get(5, TimeUnit.SECONDS);

        double[] samples1 = data1.samples();
        double[] samples2 = data2.samples();

        assertEquals(samples1.length, samples2.length);

        // Compare samples with small delta for floating point
        for (int i = 0; i < samples1.length; i++) {
            assertEquals(
                    samples1[i],
                    samples2[i],
                    0.0000001,
                    "Sample at index " + i + " differs between reads");
        }
    }

    @Test
    void testOverlappingReadsConsistent() throws Exception {
        // Read overlapping ranges
        AudioData data1 = reader.readSamples(SAMPLE_WAV, 1000, 2000).get(5, TimeUnit.SECONDS);
        AudioData data2 = reader.readSamples(SAMPLE_WAV, 1500, 2000).get(5, TimeUnit.SECONDS);

        double[] samples1 = data1.samples();
        double[] samples2 = data2.samples();

        // The overlap is frames 1500-2999 (1500 frames)
        // In data1, frame 1500 is at index 500
        // In data2, frame 1500 is at index 0
        for (int i = 0; i < 1500; i++) {
            int index1 = 500 + i;
            int index2 = i;
            assertEquals(
                    samples1[index1],
                    samples2[index2],
                    0.0000001,
                    "Overlapping sample at position " + i + " differs");
        }
    }

    // ========== Parallel Performance Tests ==========

    @Test
    @Timeout(10)
    void testParallelReadsAreFasterThanSequential() throws Exception {
        // Create a reader with controlled parallelism
        FmodParallelSampleReader parallelReader = new FmodParallelSampleReader(libraryLoader, 4);

        try {
            // Define multiple read requests
            List<ReadRequest> requests =
                    Arrays.asList(
                            new ReadRequest(0, 10000),
                            new ReadRequest(10000, 10000),
                            new ReadRequest(20000, 10000),
                            new ReadRequest(30000, 10000),
                            new ReadRequest(40000, 10000));

            // Time parallel reads
            long startParallel = System.nanoTime();
            List<CompletableFuture<AudioData>> futures =
                    requests.stream()
                            .map(
                                    req ->
                                            parallelReader.readSamples(
                                                    SAMPLE_WAV, req.startFrame(), req.frameCount()))
                            .collect(Collectors.toList());

            List<AudioData> parallelResults =
                    futures.stream()
                            .map(
                                    f -> {
                                        try {
                                            return f.get(5, TimeUnit.SECONDS);
                                        } catch (Exception e) {
                                            throw new RuntimeException(e);
                                        }
                                    })
                            .collect(Collectors.toList());
            long parallelTime = System.nanoTime() - startParallel;

            // Time sequential reads (using single-threaded reader)
            FmodParallelSampleReader sequentialReader =
                    new FmodParallelSampleReader(libraryLoader, 1);
            long startSequential = System.nanoTime();
            List<AudioData> sequentialResults = new ArrayList<>();
            for (ReadRequest req : requests) {
                sequentialResults.add(
                        sequentialReader
                                .readSamples(SAMPLE_WAV, req.startFrame(), req.frameCount())
                                .get(5, TimeUnit.SECONDS));
            }
            long sequentialTime = System.nanoTime() - startSequential;

            // Parallel should be faster
            double speedup = (double) sequentialTime / parallelTime;
            System.out.printf(
                    "Parallel speedup: %.2fx (parallel: %dms, sequential: %dms)%n",
                    speedup, parallelTime / 1_000_000, sequentialTime / 1_000_000);

            assertTrue(speedup > 1.5, "Parallel reads should be at least 1.5x faster");

            // Verify results are identical
            for (int i = 0; i < requests.size(); i++) {
                assertArrayEquals(
                        parallelResults.get(i).samples(),
                        sequentialResults.get(i).samples(),
                        0.0000001);
            }

            sequentialReader.close();
        } finally {
            parallelReader.close();
        }
    }

    @Test
    void testBulkReadMultiple() throws Exception {
        List<ReadRequest> requests =
                IntStream.range(0, 10)
                        .mapToObj(i -> new ReadRequest(i * 1000L, 500))
                        .collect(Collectors.toList());

        List<AudioData> results =
                reader.readMultiple(SAMPLE_WAV, requests).get(10, TimeUnit.SECONDS);

        assertEquals(requests.size(), results.size());

        for (int i = 0; i < results.size(); i++) {
            AudioData data = results.get(i);
            ReadRequest req = requests.get(i);

            assertEquals(req.startFrame(), data.startFrame());
            assertTrue(data.frameCount() <= req.frameCount());
            assertEquals(SAMPLE_WAV_RATE, data.sampleRate());
        }
    }

    // ========== Thread Safety Tests ==========

    @Test
    @Timeout(10)
    void testConcurrentReadsFromSameFile() throws Exception {
        int threadCount = 20;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        List<AudioData> results = Collections.synchronizedList(new ArrayList<>());
        AtomicReference<Exception> error = new AtomicReference<>();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(
                    () -> {
                        try {
                            startLatch.await();
                            AudioData data =
                                    reader.readSamples(SAMPLE_WAV, 0, 1000)
                                            .get(5, TimeUnit.SECONDS);
                            results.add(data);
                        } catch (Exception e) {
                            error.set(e);
                        } finally {
                            doneLatch.countDown();
                        }
                    });
        }

        startLatch.countDown(); // Start all threads
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));

        assertNull(error.get(), "Thread threw exception");
        assertEquals(threadCount, results.size());

        // All results should have the same data
        double[] reference = results.get(0).samples();
        for (int i = 1; i < results.size(); i++) {
            assertArrayEquals(reference, results.get(i).samples(), 0.0000001);
        }

        executor.shutdown();
    }

    @Test
    @Timeout(10)
    void testConcurrentReadsDifferentRanges() throws Exception {
        int threadCount = 30;
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        Map<Integer, AudioData> results = new ConcurrentHashMap<>();
        AtomicReference<Exception> error = new AtomicReference<>();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(
                    () -> {
                        try {
                            barrier.await(); // Synchronize start

                            // Each thread reads a different range
                            long startFrame = index * 1000L;
                            AudioData data =
                                    reader.readSamples(SAMPLE_WAV, startFrame, 500)
                                            .get(5, TimeUnit.SECONDS);

                            results.put(index, data);

                            // Verify data properties
                            assertEquals(startFrame, data.startFrame());
                            assertTrue(data.frameCount() <= 500);

                        } catch (Exception e) {
                            error.set(e);
                        } finally {
                            doneLatch.countDown();
                        }
                    });
        }

        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
        assertNull(error.get(), "Thread threw exception");
        assertEquals(threadCount, results.size());

        executor.shutdown();
    }

    @Test
    @Timeout(30)
    void testStressTestManyParallelReads() throws Exception {
        int totalReads = 100;
        Random random = new Random(42); // Deterministic for reproducibility

        List<CompletableFuture<AudioData>> futures = new ArrayList<>();

        for (int i = 0; i < totalReads; i++) {
            // Random ranges within the file
            long startFrame = random.nextInt(SAMPLE_WAV_FRAMES - 1000);
            long frameCount = 100 + random.nextInt(900);
            Path file = random.nextBoolean() ? SAMPLE_WAV : SWEEP_WAV;

            futures.add(reader.readSamples(file, startFrame, frameCount));
        }

        // Wait for all to complete
        CompletableFuture<Void> allOf =
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        allOf.get(20, TimeUnit.SECONDS);

        // Verify all completed successfully
        for (int i = 0; i < futures.size(); i++) {
            AudioData data = futures.get(i).getNow(null);
            assertNotNull(data, "Future " + i + " should be completed");

            // Verify samples in range
            for (double sample : data.samples()) {
                assertTrue(sample >= -1.0 && sample <= 1.0);
            }
        }
    }

    // ========== Resource Management Tests ==========

    @Test
    void testReaderCloseReleasesResources() throws Exception {
        FmodParallelSampleReader testReader = new FmodParallelSampleReader(libraryLoader, 2);

        // Perform some reads
        AudioData data1 = testReader.readSamples(SAMPLE_WAV, 0, 1000).get();
        assertNotNull(data1);

        // Close the reader
        testReader.close();

        // Subsequent reads should fail
        CompletableFuture<AudioData> future = testReader.readSamples(SAMPLE_WAV, 0, 1000);
        ExecutionException ex =
                assertThrows(ExecutionException.class, () -> future.get(1, TimeUnit.SECONDS));
        assertTrue(ex.getCause() instanceof AudioReadException);
        assertTrue(ex.getCause().getMessage().contains("closed"));
    }

    @Test
    void testMultipleCloseIsSafe() throws Exception {
        FmodParallelSampleReader testReader = new FmodParallelSampleReader(libraryLoader, 2);

        // Close multiple times should not throw
        testReader.close();
        testReader.close();
        testReader.close();
    }

    // ========== Edge Cases and Error Handling ==========

    @Test
    void testEmptyReadRequest() throws Exception {
        AudioData data = reader.readSamples(SAMPLE_WAV, 1000, 0).get();
        assertEquals(0, data.frameCount());
        assertEquals(0, data.samples().length);
        assertEquals(1000, data.startFrame());
    }

    @Test
    void testMetadataForNonExistentFile() {
        Path badFile = Paths.get("/not/a/real/file.wav");
        CompletableFuture<AudioMetadata> future = reader.getMetadata(badFile);

        ExecutionException ex =
                assertThrows(ExecutionException.class, () -> future.get(1, TimeUnit.SECONDS));
        assertTrue(ex.getCause() instanceof AudioReadException);
    }

    @Test
    void testDifferentParallelismLevels() throws Exception {
        // Test with different parallelism settings
        int[] parallelismLevels = {1, 2, 4, 8};

        for (int parallelism : parallelismLevels) {
            FmodParallelSampleReader testReader =
                    new FmodParallelSampleReader(libraryLoader, parallelism);

            try {
                // Run multiple concurrent reads
                List<CompletableFuture<AudioData>> futures =
                        IntStream.range(0, 10)
                                .mapToObj(i -> testReader.readSamples(SAMPLE_WAV, i * 1000L, 500))
                                .collect(Collectors.toList());

                // All should complete successfully
                for (CompletableFuture<AudioData> future : futures) {
                    AudioData data = future.get(5, TimeUnit.SECONDS);
                    assertNotNull(data);
                    assertTrue(data.frameCount() > 0);
                }

            } finally {
                testReader.close();
            }
        }
    }

    @Test
    void testConstructorValidation() {
        // Invalid parallelism
        assertThrows(
                IllegalArgumentException.class,
                () -> new FmodParallelSampleReader(libraryLoader, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new FmodParallelSampleReader(libraryLoader, -1));

        // Valid parallelism
        assertDoesNotThrow(() -> new FmodParallelSampleReader(libraryLoader, 1).close());
        assertDoesNotThrow(() -> new FmodParallelSampleReader(libraryLoader, 100).close());
    }

    // ========== Helper Methods ==========

    private void assertAudioDataValid(AudioData data, long expectedStart, long maxFrames) {
        assertNotNull(data);
        assertEquals(expectedStart, data.startFrame());
        assertTrue(data.frameCount() <= maxFrames);
        assertEquals(SAMPLE_WAV_RATE, data.sampleRate());
        assertEquals(SAMPLE_WAV_CHANNELS, data.channelCount());

        // Verify samples in valid range
        for (double sample : data.samples()) {
            assertTrue(sample >= -1.0 && sample <= 1.0);
        }
    }
}
