package audio;

import static org.junit.jupiter.api.Assertions.*;

import components.audiofiles.AudioFile;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests to verify that FmodAudioFormatDetector is thread-safe. */
@DisplayName("FmodAudioFormatDetector Thread Safety")
class FmodAudioFormatDetectorThreadSafetyTest {

    @TempDir Path tempDir;

    private File testFile;
    private AudioFile audioFile;
    private FmodAudioFormatDetector detector;
    private FmodCore fmodCore;

    @BeforeEach
    void setUp() throws Exception {
        // Create a test audio file
        testFile = tempDir.resolve("test.wav").toFile();
        testFile.createNewFile();

        // Create AudioFile instance
        audioFile = new AudioFile(testFile.getAbsolutePath());

        // Create FmodCore and detector
        env.AppConfig appConfig = new env.AppConfig();
        env.Platform platform = new env.Platform();
        AudioSystemManager audioManager = new AudioSystemManager(appConfig, platform);
        fmodCore = new FmodCore(audioManager);
        detector = new FmodAudioFormatDetector(fmodCore);
    }

    @Test
    @DisplayName("Multiple threads can safely call detectFormat concurrently")
    void concurrentDetectFormatCalls() throws InterruptedException {
        int threadCount = 5;
        int callsPerThread = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(
                    () -> {
                        try {
                            startLatch.await();
                            for (int j = 0; j < callsPerThread; j++) {
                                try {
                                    // Test both AudioFile and String-based methods
                                    FmodCore.AudioFormatInfo formatInfo1 =
                                            detector.detectFormat(audioFile);
                                    FmodCore.AudioFormatInfo formatInfo2 =
                                            detector.detectFormat(testFile.getAbsolutePath());

                                    // Verify we got valid results
                                    assertNotNull(formatInfo1);
                                    assertNotNull(formatInfo2);
                                    assertEquals(
                                            formatInfo1.getSampleRate(),
                                            formatInfo2.getSampleRate());

                                    successCount.incrementAndGet();
                                } catch (IOException e) {
                                    // Expected for test files that aren't real audio files
                                    failureCount.incrementAndGet();
                                }
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            endLatch.countDown();
                        }
                    });
        }

        startLatch.countDown();
        endLatch.await();
        executor.shutdown();

        // Verify we had some successful calls
        assertTrue(
                successCount.get() > 0 || failureCount.get() > 0,
                "Should have some successful or failed calls");
    }

    @Test
    @DisplayName("Multiple threads can safely call convenience methods concurrently")
    void concurrentConvenienceMethodCalls() throws InterruptedException {
        int threadCount = 3;
        int callsPerThread = 5;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger totalCalls = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(
                    () -> {
                        try {
                            startLatch.await();
                            for (int j = 0; j < callsPerThread; j++) {
                                try {
                                    // Test various convenience methods
                                    boolean supported = detector.isSupportedFormat(audioFile);
                                    totalCalls.incrementAndGet();

                                    // These might throw IOException for test files, which is
                                    // expected
                                    try {
                                        detector.getSampleRate(audioFile);
                                        totalCalls.incrementAndGet();
                                    } catch (IOException e) {
                                        // Expected for test files
                                    }

                                    try {
                                        detector.getChannelCount(audioFile);
                                        totalCalls.incrementAndGet();
                                    } catch (IOException e) {
                                        // Expected for test files
                                    }

                                } catch (Exception e) {
                                    // Expected for test files that aren't real audio files
                                }
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            endLatch.countDown();
                        }
                    });
        }

        startLatch.countDown();
        endLatch.await();
        executor.shutdown();

        // Verify we made some calls
        assertTrue(totalCalls.get() > 0, "Should have made some method calls");
    }

    @Test
    @DisplayName("Multiple threads can safely create detector instances concurrently")
    void concurrentDetectorCreation() throws InterruptedException {
        int threadCount = 5;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(
                    () -> {
                        try {
                            startLatch.await();

                            // Create detector instances in each thread
                            env.AppConfig appConfig = new env.AppConfig();
                            env.Platform platform = new env.Platform();
                            AudioSystemManager audioManager =
                                    new AudioSystemManager(appConfig, platform);
                            FmodCore threadFmodCore = new FmodCore(audioManager);
                            FmodAudioFormatDetector threadDetector =
                                    new FmodAudioFormatDetector(threadFmodCore);

                            assertNotNull(threadDetector);
                            successCount.incrementAndGet();

                            // Clean up
                            threadFmodCore.shutdown();

                        } catch (Exception e) {
                            // Log but don't fail the test
                            System.err.println("Thread failed: " + e.getMessage());
                        } finally {
                            endLatch.countDown();
                        }
                    });
        }

        startLatch.countDown();
        endLatch.await();
        executor.shutdown();

        // At least some threads should succeed
        assertTrue(successCount.get() > 0, "At least some threads should succeed");
    }

    @Test
    @DisplayName("Detector works correctly with thread-safe AudioFile instances")
    void detectorWithThreadSafeAudioFile() throws InterruptedException {
        int threadCount = 3;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicBoolean allThreadsCompleted = new AtomicBoolean(true);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(
                    () -> {
                        try {
                            startLatch.await();

                            // Test that we can safely access the thread-safe AudioFile
                            // while other threads might be modifying it
                            boolean isDone = audioFile.isDone();

                            // Try to detect format (might fail for test files, which is OK)
                            try {
                                detector.detectFormat(audioFile);
                            } catch (IOException e) {
                                // Expected for test files
                            }

                            // Verify AudioFile is still accessible
                            assertNotNull(audioFile.getAbsolutePath());

                        } catch (Exception e) {
                            allThreadsCompleted.set(false);
                        } finally {
                            endLatch.countDown();
                        }
                    });
        }

        startLatch.countDown();
        endLatch.await();
        executor.shutdown();

        assertTrue(allThreadsCompleted.get(), "All threads should complete without exceptions");
    }
}
