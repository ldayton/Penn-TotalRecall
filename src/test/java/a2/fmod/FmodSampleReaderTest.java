package a2.fmod;

import static org.junit.jupiter.api.Assertions.*;

import a2.AudioBuffer;
import a2.exceptions.AudioEngineException;
import annotations.AudioEngine;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Comprehensive tests for FmodSampleReader including core functionality, edge cases, and
 * concurrency.
 */
@AudioEngine
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FmodSampleReaderTest {

    private FmodLibrary fmod;
    private Pointer system;
    private FmodSampleReader sampleReader;

    @TempDir Path tempDir;

    // Test audio files
    private static final String SAMPLE_WAV = "packaging/samples/sample.wav";
    private static final String SWEEP_WAV = "packaging/samples/sweep.wav";

    // Known properties of sample.wav (mono, 44100Hz, 16-bit)
    private static final int SAMPLE_WAV_RATE = 44100;
    private static final int SAMPLE_WAV_CHANNELS = 1;

    @BeforeAll
    void setUpFmod() {
        // Load FMOD library
        String resourcePath = getClass().getResource("/fmod/macos").getPath();
        NativeLibrary.addSearchPath("fmod", resourcePath);
        fmod = Native.load("fmod", FmodLibrary.class);

        // Create FMOD system
        PointerByReference systemRef = new PointerByReference();
        int result = fmod.FMOD_System_Create(systemRef, FmodConstants.FMOD_VERSION);
        assertEquals(FmodConstants.FMOD_OK, result, "Failed to create FMOD system");
        system = systemRef.getValue();

        // Initialize FMOD system
        result = fmod.FMOD_System_Init(system, 32, FmodConstants.FMOD_INIT_NORMAL, null);
        assertEquals(FmodConstants.FMOD_OK, result, "Failed to initialize FMOD system");
    }

    @BeforeEach
    void createSampleReader() {
        sampleReader = new FmodSampleReader(fmod, system);
    }

    @AfterAll
    void tearDownFmod() {
        if (system != null && fmod != null) {
            fmod.FMOD_System_Release(system);
        }
    }

    // ========== Core Tests ==========

    @Test
    void testReadSamplesInValidRange() {
        AudioBuffer buffer = sampleReader.readSamples(SAMPLE_WAV, 0, 44100); // 1 second
        try {
            assertNotNull(buffer);
            assertNotNull(buffer.getSamples());

            double[] samples = buffer.getSamples();
            assertTrue(samples.length > 0);

            // Verify all samples are in valid range [-1.0, 1.0]
            for (int i = 0; i < samples.length; i++) {
                double sample = samples[i];
                assertTrue(
                        sample >= -1.0 && sample <= 1.0,
                        "Sample at index " + i + " out of range: " + sample);
            }
        } finally {
            buffer.close();
        }
    }

    @Test
    void testPcmConversionAccuracy() {
        // Read a small range to test conversion
        AudioBuffer buffer = sampleReader.readSamples(SAMPLE_WAV, 1000, 100);
        try {
            double[] samples = buffer.getSamples();
            assertEquals(100, samples.length, "Should have exactly 100 samples for mono");

            // Test specific conversion edge cases
            // For 16-bit: max positive = 32767 -> 1.0, max negative = -32768 -> -1.0
            // The actual samples should be properly normalized
            boolean hasPositive = false;
            boolean hasNegative = false;
            for (double sample : samples) {
                if (sample > 0) hasPositive = true;
                if (sample < 0) hasNegative = true;
            }
            // Most audio files have both positive and negative samples
            assertTrue(hasPositive || hasNegative, "Should have non-zero samples");

        } finally {
            buffer.close();
        }
    }

    @Test
    void testReadPastEofReturnsTruncated() {
        // Get total frames in the file first
        IntByReference lengthRef = new IntByReference();
        PointerByReference soundRef = new PointerByReference();
        int result =
                fmod.FMOD_System_CreateSound(
                        system, SAMPLE_WAV, FmodConstants.FMOD_OPENONLY, null, soundRef);
        assertEquals(FmodConstants.FMOD_OK, result);
        Pointer sound = soundRef.getValue();

        result = fmod.FMOD_Sound_GetLength(sound, lengthRef, FmodConstants.FMOD_TIMEUNIT_PCM);
        assertEquals(FmodConstants.FMOD_OK, result);
        int totalFrames = lengthRef.getValue();
        fmod.FMOD_Sound_Release(sound);

        // Try to read past end
        long startFrame = totalFrames - 100;
        long requestedFrames = 200; // Request more than available

        AudioBuffer buffer = sampleReader.readSamples(SAMPLE_WAV, startFrame, requestedFrames);
        try {
            assertEquals(100, buffer.getFrameCount(), "Should only return available frames");
            assertEquals(100, buffer.getSamples().length, "Sample array should match frame count");
            assertEquals(startFrame, buffer.getStartFrame());
        } finally {
            buffer.close();
        }
    }

    @Test
    void testReadBeyondEofReturnsEmpty() {
        // Start reading way past the end of file
        AudioBuffer buffer = sampleReader.readSamples(SAMPLE_WAV, 10000000, 1000);
        try {
            assertNotNull(buffer);
            assertEquals(0, buffer.getFrameCount());
            assertEquals(0, buffer.getSamples().length);
        } finally {
            buffer.close();
        }
    }

    @Test
    void testInvalidInputsThrowExceptions() {
        // Negative start frame
        assertThrows(
                AudioEngineException.class, () -> sampleReader.readSamples(SAMPLE_WAV, -1, 100));

        // Negative frame count
        assertThrows(
                AudioEngineException.class, () -> sampleReader.readSamples(SAMPLE_WAV, 0, -100));

        // Null file path
        assertThrows(Exception.class, () -> sampleReader.readSamples(null, 0, 100));

        // Non-existent file
        assertThrows(
                AudioEngineException.class,
                () -> sampleReader.readSamples("/does/not/exist.wav", 0, 100));

        // Directory instead of file
        assertThrows(
                AudioEngineException.class,
                () -> sampleReader.readSamples("packaging/samples", 0, 100));
    }

    @Test
    void testBufferMetadataMatchesFile() {
        AudioBuffer buffer = sampleReader.readSamples(SAMPLE_WAV, 0, 1000);
        try {
            assertEquals(SAMPLE_WAV_RATE, buffer.getSampleRate());
            assertEquals(SAMPLE_WAV_CHANNELS, buffer.getChannelCount());
            assertEquals(0, buffer.getStartFrame());
            assertEquals(1000, buffer.getFrameCount());
            assertFalse(buffer.isClosed());

            // Test close behavior
            buffer.close();
            assertTrue(buffer.isClosed());
            assertNull(buffer.getSamples(), "Closed buffer should return null samples");
        } finally {
            if (!buffer.isClosed()) {
                buffer.close();
            }
        }
    }

    // ========== Nice-to-Have Tests ==========

    @Test
    void testConsecutiveReadsConsistent() {
        // Read the same range twice
        AudioBuffer buffer1 = sampleReader.readSamples(SAMPLE_WAV, 5000, 1000);
        AudioBuffer buffer2 = sampleReader.readSamples(SAMPLE_WAV, 5000, 1000);

        try {
            double[] samples1 = buffer1.getSamples();
            double[] samples2 = buffer2.getSamples();

            assertEquals(samples1.length, samples2.length);

            // Compare samples with small delta for floating point
            for (int i = 0; i < samples1.length; i++) {
                assertEquals(
                        samples1[i],
                        samples2[i],
                        0.0000001,
                        "Sample at index " + i + " differs between reads");
            }
        } finally {
            buffer1.close();
            buffer2.close();
        }
    }

    @Test
    void testOverlappingReadsConsistent() {
        // Read overlapping ranges
        AudioBuffer buffer1 = sampleReader.readSamples(SAMPLE_WAV, 1000, 2000);
        AudioBuffer buffer2 = sampleReader.readSamples(SAMPLE_WAV, 1500, 2000);

        try {
            double[] samples1 = buffer1.getSamples();
            double[] samples2 = buffer2.getSamples();

            // Buffer1 covers frames 1000-2999, Buffer2 covers frames 1500-3499
            // The overlap is frames 1500-2999 (1500 frames)
            // In buffer1, frame 1500 is at index 500 (1500-1000)
            // In buffer2, frame 1500 is at index 0
            for (int i = 0; i < 1500; i++) {
                int index1 = 500 + i; // Starting from sample 500 in buffer1
                int index2 = i; // Starting from beginning of buffer2
                assertEquals(
                        samples1[index1],
                        samples2[index2],
                        0.0000001,
                        "Overlapping sample at position " + i + " differs");
            }
        } finally {
            buffer1.close();
            buffer2.close();
        }
    }

    @Test
    void testMonoChannelHandling() {
        // sample.wav is mono
        AudioBuffer buffer = sampleReader.readSamples(SAMPLE_WAV, 0, 100);
        try {
            assertEquals(1, buffer.getChannelCount());
            // For mono, samples array length equals frame count
            assertEquals(buffer.getFrameCount(), buffer.getSamples().length);
        } finally {
            buffer.close();
        }
    }

    @Test
    void testDifferentRanges() {
        // Test beginning
        AudioBuffer beginning = sampleReader.readSamples(SAMPLE_WAV, 0, 100);
        // Test middle
        AudioBuffer middle = sampleReader.readSamples(SAMPLE_WAV, 22050, 100);
        // Test near end
        AudioBuffer nearEnd = sampleReader.readSamples(SAMPLE_WAV, 44000, 100);

        try {
            // All should have requested frame count
            assertEquals(100, beginning.getFrameCount());
            assertEquals(100, middle.getFrameCount());
            assertEquals(100, nearEnd.getFrameCount());

            // All should have correct start frames
            assertEquals(0, beginning.getStartFrame());
            assertEquals(22050, middle.getStartFrame());
            assertEquals(44000, nearEnd.getStartFrame());

            // Samples should be different between ranges
            double[] beginSamples = beginning.getSamples();
            double[] midSamples = middle.getSamples();

            // Calculate simple difference metric
            double diff = 0;
            for (int i = 0; i < 100; i++) {
                diff += Math.abs(beginSamples[i] - midSamples[i]);
            }
            assertTrue(diff > 0.1, "Different parts of file should have different samples");

        } finally {
            beginning.close();
            middle.close();
            nearEnd.close();
        }
    }

    // ========== Complex Concurrency Tests ==========

    @Test
    @Timeout(10)
    void testConcurrentReadsFromSameFile() throws Exception {
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        List<AudioBuffer> buffers = new ArrayList<>();
        AtomicReference<Exception> error = new AtomicReference<>();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(
                    () -> {
                        try {
                            startLatch.await();
                            AudioBuffer buffer = sampleReader.readSamples(SAMPLE_WAV, 0, 1000);
                            synchronized (buffers) {
                                buffers.add(buffer);
                            }
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
        assertEquals(threadCount, buffers.size());

        // All buffers should have the same data
        double[] reference = buffers.get(0).getSamples();
        for (int i = 1; i < buffers.size(); i++) {
            double[] samples = buffers.get(i).getSamples();
            assertArrayEquals(reference, samples, 0.0000001);
        }

        // Cleanup
        for (AudioBuffer buffer : buffers) {
            buffer.close();
        }
        executor.shutdown();
    }

    @Test
    @Timeout(10)
    void testConcurrentReadsDifferentRanges() throws Exception {
        int threadCount = 20;
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        List<AudioBuffer> buffers = new ArrayList<>();
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
                            AudioBuffer buffer =
                                    sampleReader.readSamples(SAMPLE_WAV, startFrame, 500);

                            synchronized (buffers) {
                                buffers.add(buffer);
                            }

                            // Verify buffer properties
                            assertEquals(startFrame, buffer.getStartFrame());
                            assertTrue(buffer.getFrameCount() <= 500);

                        } catch (Exception e) {
                            error.set(e);
                        } finally {
                            doneLatch.countDown();
                        }
                    });
        }

        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
        assertNull(error.get(), "Thread threw exception");
        assertEquals(threadCount, buffers.size());

        // Cleanup
        for (AudioBuffer buffer : buffers) {
            buffer.close();
        }
        executor.shutdown();
    }

    @Test
    @Timeout(10)
    void testConcurrentReadsDifferentFiles() throws Exception {
        int threadCount = 20;
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        AtomicReference<Exception> error = new AtomicReference<>();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(
                    () -> {
                        try {
                            barrier.await();

                            // Alternate between two files
                            String file = (index % 2 == 0) ? SAMPLE_WAV : SWEEP_WAV;
                            AudioBuffer buffer = sampleReader.readSamples(file, 0, 1000);

                            assertNotNull(buffer);
                            assertNotNull(buffer.getSamples());
                            assertTrue(buffer.getSamples().length > 0);

                            buffer.close();
                            successCount.incrementAndGet();

                        } catch (Exception e) {
                            error.set(e);
                        } finally {
                            doneLatch.countDown();
                        }
                    });
        }

        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
        assertNull(error.get(), "Thread threw exception");
        assertEquals(threadCount, successCount.get());

        executor.shutdown();
    }

    @Test
    @Timeout(30)
    void testStressTestManyParallelReads() throws Exception {
        int threadCount = 50;
        int readsPerThread = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<Integer>> futures = new ArrayList<>();
        AtomicReference<Exception> error = new AtomicReference<>();

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            Future<Integer> future =
                    executor.submit(
                            () -> {
                                int successfulReads = 0;
                                try {
                                    for (int i = 0; i < readsPerThread; i++) {
                                        // Mix of different operations
                                        long startFrame = (threadId * 100L + i * 50) % 40000;
                                        long frameCount = 50 + (i * 10);
                                        String file =
                                                (threadId + i) % 3 == 0 ? SWEEP_WAV : SAMPLE_WAV;

                                        AudioBuffer buffer =
                                                sampleReader.readSamples(
                                                        file, startFrame, frameCount);

                                        // Basic validation
                                        assertNotNull(buffer);
                                        assertTrue(buffer.getFrameCount() >= 0);

                                        // Verify samples in range
                                        double[] samples = buffer.getSamples();
                                        for (double sample : samples) {
                                            assertTrue(sample >= -1.0 && sample <= 1.0);
                                        }

                                        buffer.close();
                                        successfulReads++;
                                    }
                                } catch (Exception e) {
                                    error.compareAndSet(null, e);
                                }
                                return successfulReads;
                            });
            futures.add(future);
        }

        // Wait for all and count successes
        int totalSuccessful = 0;
        for (Future<Integer> future : futures) {
            totalSuccessful += future.get(20, TimeUnit.SECONDS);
        }

        assertNull(error.get(), "Thread threw exception");
        assertEquals(
                threadCount * readsPerThread,
                totalSuccessful,
                "All reads should complete successfully");

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }
}
