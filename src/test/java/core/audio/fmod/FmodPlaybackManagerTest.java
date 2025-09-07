package audio.fmod;

import static org.junit.jupiter.api.Assertions.*;

import annotations.Audio;
import audio.AudioHandle;
import audio.exceptions.AudioPlaybackException;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;

/**
 * Integration tests for FmodPlaybackManager. Tests the playback management logic with real FMOD
 * operations.
 */
@Audio
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
class FmodPlaybackManagerTest {

    private FmodLibrary fmod;
    private Pointer system;
    private FmodPlaybackManager playbackManager;

    // For loading test sounds
    private FmodSystemStateManager stateManager;
    private FmodHandleLifecycleManager lifecycleManager;
    private FmodAudioLoadingManager loadingManager;

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

        // Create state manager for loading manager
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
    void createManagers() {
        // Create fresh managers for each test
        playbackManager = new FmodPlaybackManager(fmod, system);
        lifecycleManager = new FmodHandleLifecycleManager();
        loadingManager = new FmodAudioLoadingManager(fmod, system, stateManager, lifecycleManager);
    }

    @AfterAll
    void tearDownFmod() {
        if (loadingManager != null) {
            loadingManager.releaseAll();
        }
        if (system != null && fmod != null) {
            fmod.FMOD_System_Release(system);
        }
    }

    // Helper method to load and get sound
    private Pointer loadSound(String filePath) throws Exception {
        loadingManager.loadAudio(filePath);
        return loadingManager
                .getCurrentSound()
                .orElseThrow(() -> new IllegalStateException("Sound not loaded"));
    }

    // ========== Core Playback Tests ==========

    @Test
    void testPlayFullAudio() throws Exception {
        AudioHandle handle = loadingManager.loadAudio(SAMPLE_WAV);
        Pointer sound = loadSound(SAMPLE_WAV);

        FmodPlaybackHandle playbackHandle = playbackManager.play(sound, handle);

        assertNotNull(playbackHandle);
        assertTrue(playbackHandle.isActive());
        assertEquals(handle, playbackHandle.getAudioHandle());
        assertEquals(0, playbackHandle.getStartFrame());
        assertTrue(playbackManager.hasActivePlayback());

        // Let it play for a bit
        Thread.sleep(100);

        // Verify position advanced
        long position = playbackManager.getPosition();
        assertTrue(position > 0, "Position should advance during playback");

        playbackManager.stop();
        assertFalse(playbackHandle.isActive());
        assertFalse(playbackManager.hasActivePlayback());
    }

    @Test
    void testPlayStopPlay() throws Exception {
        AudioHandle handle = loadingManager.loadAudio(SAMPLE_WAV);
        Pointer sound = loadSound(SAMPLE_WAV);

        // First play
        FmodPlaybackHandle handle1 = playbackManager.play(sound, handle);
        assertTrue(handle1.isActive());
        Thread.sleep(50);
        long position1 = playbackManager.getPosition();
        assertTrue(position1 > 0);

        // Stop
        playbackManager.stop();
        assertFalse(handle1.isActive());
        assertEquals(0, playbackManager.getPosition());

        // Play again
        FmodPlaybackHandle handle2 = playbackManager.play(sound, handle);
        assertTrue(handle2.isActive());
        assertFalse(handle1.isActive()); // First handle should remain inactive
        Thread.sleep(50);
        long position2 = playbackManager.getPosition();
        assertTrue(position2 > 0);

        playbackManager.stop();
    }

    @Test
    void testPlayMultipleSounds() throws Exception {
        log.warn("=== EXPECTED WARNING FOLLOWS - Testing channel cleanup on multiple plays ===");
        // Load first sound
        AudioHandle handle1 = loadingManager.loadAudio(SAMPLE_WAV);
        Pointer sound1 = loadSound(SAMPLE_WAV);

        // Play first sound
        FmodPlaybackHandle playback1 = playbackManager.play(sound1, handle1);
        assertTrue(playback1.isActive());
        Thread.sleep(50);

        // Load and play second sound - should stop first
        AudioHandle handle2 = loadingManager.loadAudio(SWEEP_WAV);
        Pointer sound2 = loadSound(SWEEP_WAV);

        FmodPlaybackHandle playback2 = playbackManager.play(sound2, handle2);
        assertTrue(playback2.isActive());
        assertFalse(playback1.isActive());

        // Only second playback should be active
        assertTrue(playbackManager.getCurrentPlayback().isPresent());
        assertEquals(playback2, playbackManager.getCurrentPlayback().get());

        playbackManager.stop();
    }

    @Test
    void testPauseResume() throws Exception {
        AudioHandle handle = loadingManager.loadAudio(SAMPLE_WAV);
        Pointer sound = loadSound(SAMPLE_WAV);

        playbackManager.play(sound, handle);
        Thread.sleep(100);

        long positionBeforePause = playbackManager.getPosition();
        assertTrue(positionBeforePause > 0);

        // Pause
        playbackManager.pause();
        Thread.sleep(100);

        // Position shouldn't change while paused
        long positionDuringPause = playbackManager.getPosition();
        assertEquals(positionBeforePause, positionDuringPause, 100); // Allow small variance

        // Resume
        playbackManager.resume();
        Thread.sleep(100);

        // Position should advance after resume
        long positionAfterResume = playbackManager.getPosition();
        assertTrue(positionAfterResume > positionDuringPause);

        playbackManager.stop();
    }

    @Test
    void testSeekDuringPlayback() throws Exception {
        AudioHandle handle = loadingManager.loadAudio(SAMPLE_WAV);
        Pointer sound = loadSound(SAMPLE_WAV);

        playbackManager.play(sound, handle);

        // Pause to ensure position doesn't advance during seek
        playbackManager.pause();

        // Seek to middle
        long targetFrame = 22050; // ~0.5 seconds at 44100 Hz
        playbackManager.seek(targetFrame);

        long position = playbackManager.getPosition();
        // Should be exact when paused
        assertEquals(targetFrame, position);

        // Seek forward
        targetFrame = 44100; // ~1 second
        playbackManager.seek(targetFrame);
        position = playbackManager.getPosition();
        assertEquals(targetFrame, position);

        // Seek backward
        targetFrame = 11025; // ~0.25 seconds
        playbackManager.seek(targetFrame);
        position = playbackManager.getPosition();
        assertEquals(targetFrame, position);

        // Resume and verify playback continues from seek position
        playbackManager.resume();
        Thread.sleep(100);
        long resumedPosition = playbackManager.getPosition();
        assertTrue(resumedPosition > targetFrame, "Should advance after resume");

        playbackManager.stop();
    }

    @Test
    void testPlayRange() throws Exception {
        AudioHandle handle = loadingManager.loadAudio(SAMPLE_WAV);
        Pointer sound = loadSound(SAMPLE_WAV);

        long startFrame = 22050; // 0.5 seconds
        long endFrame = 66150; // 1.5 seconds

        FmodPlaybackHandle playbackHandle =
                playbackManager.playRange(sound, handle, startFrame, endFrame, true);

        assertNotNull(playbackHandle);
        assertEquals(startFrame, playbackHandle.getStartFrame());
        assertEquals(endFrame, playbackHandle.getEndFrame());

        // Let it play briefly
        Thread.sleep(100);

        // Position should be after start frame
        long position = playbackManager.getPosition();
        assertTrue(position >= startFrame, "Position should be at or after start frame");

        playbackManager.stop();
    }

    @Test
    void testGetPositionDuringPlayback() throws Exception {
        AudioHandle handle = loadingManager.loadAudio(SAMPLE_WAV);
        Pointer sound = loadSound(SAMPLE_WAV);

        playbackManager.play(sound, handle);

        long lastPosition = 0;
        for (int i = 0; i < 5; i++) {
            Thread.sleep(50);
            long position = playbackManager.getPosition();
            assertTrue(position >= lastPosition, "Position should not go backward");
            lastPosition = position;
        }

        assertTrue(lastPosition > 0, "Position should advance during playback");
        playbackManager.stop();
    }

    @Test
    void testCheckPlaybackFinished() throws Exception {
        // Create a very short sound by loading just a small range
        // Note: This requires creating a sound with limited duration
        // For now, we'll test the mechanism works
        AudioHandle handle = loadingManager.loadAudio(SAMPLE_WAV);
        Pointer sound = loadSound(SAMPLE_WAV);

        playbackManager.play(sound, handle);
        assertFalse(playbackManager.checkPlaybackFinished());

        // Stop playback manually
        playbackManager.stop();

        // After stop, there's no playback to check
        assertFalse(playbackManager.checkPlaybackFinished());
        assertFalse(playbackManager.hasActivePlayback());
    }

    @Test
    void testHasActivePlayback() throws Exception {
        assertFalse(playbackManager.hasActivePlayback());

        AudioHandle handle = loadingManager.loadAudio(SAMPLE_WAV);
        Pointer sound = loadSound(SAMPLE_WAV);

        playbackManager.play(sound, handle);
        assertTrue(playbackManager.hasActivePlayback());

        playbackManager.pause();
        assertTrue(playbackManager.hasActivePlayback()); // Still active when paused

        playbackManager.stop();
        assertFalse(playbackManager.hasActivePlayback());
    }

    @Test
    void testGetCurrentPlayback() throws Exception {
        assertFalse(playbackManager.getCurrentPlayback().isPresent());

        AudioHandle handle = loadingManager.loadAudio(SAMPLE_WAV);
        Pointer sound = loadSound(SAMPLE_WAV);

        FmodPlaybackHandle playbackHandle = playbackManager.play(sound, handle);

        assertTrue(playbackManager.getCurrentPlayback().isPresent());
        assertEquals(playbackHandle, playbackManager.getCurrentPlayback().get());

        playbackManager.stop();
        assertFalse(playbackManager.getCurrentPlayback().isPresent());
    }

    @Test
    void testStopCleansUpResources() throws Exception {
        AudioHandle handle = loadingManager.loadAudio(SAMPLE_WAV);
        Pointer sound = loadSound(SAMPLE_WAV);

        FmodPlaybackHandle playbackHandle = playbackManager.play(sound, handle);
        assertTrue(playbackHandle.isActive());

        playbackManager.stop();

        assertFalse(playbackHandle.isActive());
        assertFalse(playbackManager.hasActivePlayback());
        assertEquals(0, playbackManager.getPosition());
        assertFalse(playbackManager.getCurrentPlayback().isPresent());

        // Should be able to play again
        FmodPlaybackHandle newHandle = playbackManager.play(sound, handle);
        assertTrue(newHandle.isActive());
        playbackManager.stop();
    }

    @Test
    void testOperationsOnStoppedPlayback() throws Exception {
        // No active playback
        assertThrows(AudioPlaybackException.class, () -> playbackManager.pause());
        assertThrows(AudioPlaybackException.class, () -> playbackManager.resume());
        assertThrows(AudioPlaybackException.class, () -> playbackManager.seek(1000));

        // Start and stop playback
        AudioHandle handle = loadingManager.loadAudio(SAMPLE_WAV);
        Pointer sound = loadSound(SAMPLE_WAV);

        playbackManager.play(sound, handle);
        playbackManager.stop();

        // Operations should fail after stop
        assertThrows(AudioPlaybackException.class, () -> playbackManager.pause());
        assertThrows(AudioPlaybackException.class, () -> playbackManager.resume());
        assertThrows(AudioPlaybackException.class, () -> playbackManager.seek(1000));
    }

    @Test
    @Timeout(5)
    void testConcurrentPositionQueries() throws Exception {
        AudioHandle handle = loadingManager.loadAudio(SAMPLE_WAV);
        Pointer sound = loadSound(SAMPLE_WAV);

        playbackManager.play(sound, handle);

        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch latch = new CountDownLatch(5);
        AtomicBoolean error = new AtomicBoolean(false);

        for (int i = 0; i < 5; i++) {
            executor.submit(
                    () -> {
                        try {
                            for (int j = 0; j < 100; j++) {
                                long position = playbackManager.getPosition();
                                assertTrue(position >= 0);
                                Thread.sleep(1);
                            }
                        } catch (Exception e) {
                            error.set(true);
                        } finally {
                            latch.countDown();
                        }
                    });
        }

        assertTrue(latch.await(4, TimeUnit.SECONDS));
        assertFalse(error.get());

        executor.shutdown();
        playbackManager.stop();
    }

    @Test
    @Timeout(5)
    void testRapidPlayStopCycles() throws Exception {
        AudioHandle handle = loadingManager.loadAudio(SAMPLE_WAV);
        Pointer sound = loadSound(SAMPLE_WAV);

        for (int i = 0; i < 20; i++) {
            FmodPlaybackHandle playbackHandle = playbackManager.play(sound, handle);
            assertTrue(playbackHandle.isActive());

            Thread.sleep(10); // Play briefly

            playbackManager.stop();
            assertFalse(playbackHandle.isActive());
            assertFalse(playbackManager.hasActivePlayback());
        }
    }

    @Test
    void testPlaybackHandleInvalidation() throws Exception {
        AudioHandle handle = loadingManager.loadAudio(SAMPLE_WAV);
        Pointer sound = loadSound(SAMPLE_WAV);

        // Create multiple handles by playing multiple times
        FmodPlaybackHandle handle1 = playbackManager.play(sound, handle);
        assertTrue(handle1.isActive());

        Thread.sleep(50);

        FmodPlaybackHandle handle2 = playbackManager.play(sound, handle);
        assertTrue(handle2.isActive());
        assertFalse(handle1.isActive()); // First handle should be invalidated

        Thread.sleep(50);

        FmodPlaybackHandle handle3 = playbackManager.play(sound, handle);
        assertTrue(handle3.isActive());
        assertFalse(handle1.isActive());
        assertFalse(handle2.isActive()); // Both previous handles should be invalidated

        playbackManager.stop();
        assertFalse(handle3.isActive());
    }

    @Test
    @Timeout(5)
    void testConcurrentPlayRequests() throws Exception {
        AudioHandle handle = loadingManager.loadAudio(SAMPLE_WAV);
        Pointer sound = loadSound(SAMPLE_WAV);

        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();

        for (int i = 0; i < 10; i++) {
            executor.submit(
                    () -> {
                        try {
                            startLatch.await();
                            playbackManager.play(sound, handle);
                            successCount.incrementAndGet();
                        } catch (Exception e) {
                            // Some may fail due to concurrent cleanup - this is expected
                        }
                    });
        }

        startLatch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));

        // At least one should succeed
        assertTrue(successCount.get() > 0);
        // Only one playback should be active
        assertTrue(playbackManager.hasActivePlayback());

        playbackManager.stop();
    }
}
