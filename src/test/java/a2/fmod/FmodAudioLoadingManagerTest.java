package a2.fmod;

import static org.junit.jupiter.api.Assertions.*;

import a2.AudioHandle;
import a2.AudioMetadata;
import a2.exceptions.AudioLoadException;
import annotations.AudioEngine;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
 * High-value tests for FmodAudioLoadingManager focusing on concurrency, resource management, and
 * error handling.
 */
@AudioEngine
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FmodAudioLoadingManagerTest {

    private FmodLibrary fmod;
    private Pointer system;
    private FmodSystemStateManager stateManager;
    private FmodAudioLoadingManager loadingManager;

    @TempDir Path tempDir;

    // Test audio files
    private static final String SAMPLE_WAV = "packaging/samples/sample.wav";
    private static final String SWEEP_WAV = "packaging/samples/sweep.wav";

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

        // Create state manager and set to INITIALIZED
        stateManager = new FmodSystemStateManager();
        assertTrue(
                stateManager.compareAndSetState(
                        FmodSystemStateManager.State.UNINITIALIZED,
                        FmodSystemStateManager.State.INITIALIZING));
        assertTrue(
                stateManager.compareAndSetState(
                        FmodSystemStateManager.State.INITIALIZING,
                        FmodSystemStateManager.State.INITIALIZED));
    }

    @BeforeEach
    void resetLoadingManager() {
        if (loadingManager != null) {
            loadingManager.releaseAll();
        }
        // Create fresh loading manager for each test
        loadingManager = new FmodAudioLoadingManager(fmod, system, stateManager);
    }

    @AfterAll
    void tearDownFmod() {
        if (system != null && fmod != null) {
            fmod.FMOD_System_Release(system);
        }
    }

    // ========== Core Loading Behavior Tests ==========

    @Test
    void testLoadAudioLifecycle() throws Exception {
        // Load first file
        AudioHandle handle1 = loadingManager.loadAudio(SAMPLE_WAV);
        assertNotNull(handle1);
        assertTrue(handle1.isValid());
        assertEquals(new File(SAMPLE_WAV).getCanonicalPath(), handle1.getFilePath());

        // Verify it's current
        Optional<FmodAudioHandle> current = loadingManager.getCurrentHandle();
        assertTrue(current.isPresent());
        assertEquals(handle1, current.get());

        // Load different file - should release first
        AudioHandle handle2 = loadingManager.loadAudio(SWEEP_WAV);
        assertNotNull(handle2);
        assertNotEquals(handle1.getId(), handle2.getId());
        assertEquals(new File(SWEEP_WAV).getCanonicalPath(), handle2.getFilePath());

        // Verify second is now current
        current = loadingManager.getCurrentHandle();
        assertTrue(current.isPresent());
        assertEquals(handle2, current.get());
        assertFalse(loadingManager.isCurrent(handle1));
        assertTrue(loadingManager.isCurrent(handle2));
    }

    @Test
    void testLoadSameFileDeduplication() throws Exception {
        // Load file first time
        AudioHandle handle1 = loadingManager.loadAudio(SAMPLE_WAV);
        assertNotNull(handle1);

        // Load same file again - should get same handle
        AudioHandle handle2 = loadingManager.loadAudio(SAMPLE_WAV);
        assertSame(handle1, handle2);
        assertEquals(handle1.getId(), handle2.getId());

        // Test with different path representation
        String altPath = "./packaging/samples/sample.wav";
        AudioHandle handle3 = loadingManager.loadAudio(altPath);
        assertSame(handle1, handle3);
    }

    @Test
    void testLoadNonExistentFile() {
        String nonExistent = "/does/not/exist/audio.wav";

        AudioLoadException ex =
                assertThrows(AudioLoadException.class, () -> loadingManager.loadAudio(nonExistent));

        assertTrue(ex.getMessage().contains("Audio file not found"));
        assertTrue(ex.getMessage().contains(nonExistent));

        // Verify no audio is loaded
        assertFalse(loadingManager.getCurrentHandle().isPresent());
    }

    @Test
    void testLoadInvalidAudioFile() throws Exception {
        // Create a text file with .wav extension
        Path fakeWav = tempDir.resolve("fake.wav");
        Files.writeString(fakeWav, "This is not audio data");

        assertThrows(Exception.class, () -> loadingManager.loadAudio(fakeWav.toString()));

        // Verify no audio is loaded
        assertFalse(loadingManager.getCurrentHandle().isPresent());
    }

    // ========== Metadata Extraction Test ==========

    @Test
    void testMetadataExtraction() throws Exception {
        // Test with no audio loaded
        Optional<AudioMetadata> metadata = loadingManager.getCurrentMetadata();
        assertFalse(metadata.isPresent());

        // Load sample.wav and verify metadata
        loadingManager.loadAudio(SAMPLE_WAV);
        metadata = loadingManager.getCurrentMetadata();
        assertTrue(metadata.isPresent());

        AudioMetadata meta = metadata.get();
        assertEquals("WAV", meta.format());
        assertEquals(44100, meta.sampleRate()); // Sample.wav is 44.1kHz
        assertEquals(1, meta.channelCount()); // Mono
        assertTrue(meta.durationSeconds() > 0);
        assertTrue(meta.frameCount() > 0);

        // Load different file with different metadata
        loadingManager.loadAudio(SWEEP_WAV);
        metadata = loadingManager.getCurrentMetadata();
        assertTrue(metadata.isPresent());

        AudioMetadata meta2 = metadata.get();
        assertEquals("WAV", meta2.format());
        // Sweep might have different duration
        assertNotEquals(meta.durationSeconds(), meta2.durationSeconds());
    }

    // ========== Resource Management Tests ==========

    @Test
    void testReleaseAllCleansUpProperly() throws Exception {
        // Load audio
        AudioHandle handle = loadingManager.loadAudio(SAMPLE_WAV);
        assertNotNull(handle);
        assertTrue(loadingManager.getCurrentHandle().isPresent());
        assertTrue(loadingManager.getCurrentSound().isPresent());

        // Release all
        loadingManager.releaseAll();

        // Verify everything is cleaned up
        assertFalse(loadingManager.getCurrentHandle().isPresent());
        assertFalse(loadingManager.getCurrentSound().isPresent());
        assertFalse(loadingManager.getCurrentMetadata().isPresent());
        assertFalse(loadingManager.isCurrent(handle));

        // Verify we can load again (no leaked state)
        AudioHandle handle2 = loadingManager.loadAudio(SWEEP_WAV);
        assertNotNull(handle2);
        assertTrue(loadingManager.getCurrentHandle().isPresent());
    }

    @Test
    void testFailedLoadDoesNotReleaseExisting() throws Exception {
        // Load valid file
        AudioHandle validHandle = loadingManager.loadAudio(SAMPLE_WAV);
        assertNotNull(validHandle);
        assertTrue(loadingManager.isCurrent(validHandle));

        // Try to load invalid file
        Path fakeWav = tempDir.resolve("bad.wav");
        Files.writeString(fakeWav, "Invalid audio data");

        assertThrows(Exception.class, () -> loadingManager.loadAudio(fakeWav.toString()));

        // Verify original is still loaded
        assertTrue(loadingManager.isCurrent(validHandle));
        Optional<FmodAudioHandle> current = loadingManager.getCurrentHandle();
        assertTrue(current.isPresent());
        assertEquals(validHandle, current.get());
    }

    // ========== Thread Safety Tests ==========

    @Test
    @Timeout(5)
    void testConcurrentLoadsSameFile() throws Exception {
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        Set<AudioHandle> handles = ConcurrentHashMap.newKeySet();
        AtomicReference<Exception> error = new AtomicReference<>();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(
                    () -> {
                        try {
                            startLatch.await();
                            AudioHandle handle = loadingManager.loadAudio(SAMPLE_WAV);
                            handles.add(handle);
                        } catch (Exception e) {
                            error.set(e);
                        } finally {
                            doneLatch.countDown();
                        }
                    });
        }

        startLatch.countDown(); // Release all threads
        assertTrue(doneLatch.await(3, TimeUnit.SECONDS));

        assertNull(error.get(), "Thread threw exception");
        // All threads should get the same handle
        assertEquals(1, handles.size());

        executor.shutdown();
    }

    @Test
    @Timeout(5)
    void testConcurrentLoadsDifferentFiles() throws Exception {
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
                            barrier.await(); // Synchronize start

                            // Alternate between two files
                            String file = (index % 2 == 0) ? SAMPLE_WAV : SWEEP_WAV;
                            AudioHandle handle = loadingManager.loadAudio(file);
                            assertNotNull(handle);

                            // Verify handle matches the file we requested
                            assertTrue(
                                    handle.getFilePath()
                                            .contains(index % 2 == 0 ? "sample.wav" : "sweep.wav"));

                            successCount.incrementAndGet();
                        } catch (Exception e) {
                            error.set(e);
                        } finally {
                            doneLatch.countDown();
                        }
                    });
        }

        assertTrue(doneLatch.await(3, TimeUnit.SECONDS));
        assertNull(error.get(), "Thread threw exception");
        assertEquals(threadCount, successCount.get());

        executor.shutdown();
    }

    @Test
    @Timeout(5)
    void testRaceConditionLoadAndRelease() throws Exception {
        int iterations = 100;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(3);
        AtomicReference<Exception> error = new AtomicReference<>();

        // Thread 1: Continuously load/release
        Thread loader =
                new Thread(
                        () -> {
                            try {
                                startLatch.await();
                                for (int i = 0; i < iterations; i++) {
                                    if (i % 2 == 0) {
                                        loadingManager.loadAudio(SAMPLE_WAV);
                                    } else {
                                        loadingManager.releaseAll();
                                    }
                                }
                            } catch (Exception e) {
                                error.set(e);
                            } finally {
                                doneLatch.countDown();
                            }
                        });

        // Thread 2: Continuously get metadata
        Thread metadataReader =
                new Thread(
                        () -> {
                            try {
                                startLatch.await();
                                for (int i = 0; i < iterations; i++) {
                                    Optional<AudioMetadata> metadata =
                                            loadingManager.getCurrentMetadata();
                                    // Just access it, don't assert (might be empty)
                                    metadata.ifPresent(m -> m.format());
                                }
                            } catch (Exception e) {
                                error.set(e);
                            } finally {
                                doneLatch.countDown();
                            }
                        });

        // Thread 3: Continuously check current handle
        Thread handleChecker =
                new Thread(
                        () -> {
                            try {
                                startLatch.await();
                                for (int i = 0; i < iterations; i++) {
                                    Optional<FmodAudioHandle> handle =
                                            loadingManager.getCurrentHandle();
                                    handle.ifPresent(h -> loadingManager.isCurrent(h));
                                }
                            } catch (Exception e) {
                                error.set(e);
                            } finally {
                                doneLatch.countDown();
                            }
                        });

        loader.start();
        metadataReader.start();
        handleChecker.start();

        startLatch.countDown();
        assertTrue(doneLatch.await(3, TimeUnit.SECONDS));

        assertNull(error.get(), "Thread threw exception");
    }

    // ========== Format and State Validation Tests ==========

    @Test
    void testSupportedFormats() throws Exception {
        // Test WAV format
        loadingManager.loadAudio(SAMPLE_WAV);
        Optional<AudioMetadata> metadata = loadingManager.getCurrentMetadata();
        assertTrue(metadata.isPresent());
        assertEquals("WAV", metadata.get().format());

        // We only have WAV samples, but the code supports:
        // MP3, OGG, FLAC, AIFF, Opus, RAW
        // Would test these if we had sample files
    }

    @Test
    void testLoadDirectoryFails() {
        String dirPath = "packaging/samples";

        AudioLoadException ex =
                assertThrows(AudioLoadException.class, () -> loadingManager.loadAudio(dirPath));

        assertTrue(ex.getMessage().contains("Path is a directory"));
    }
}
