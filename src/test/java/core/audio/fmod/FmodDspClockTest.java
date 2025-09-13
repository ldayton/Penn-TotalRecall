package core.audio.fmod;

import static org.junit.jupiter.api.Assertions.*;

import core.audio.AudioHandle;
import core.audio.fmod.panama.FmodCore;
import core.audio.fmod.panama.FmodCore_1;
import core.env.Platform;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Tests specific to DSP clock functionality in FMOD. Verifies DSP clock behavior, accuracy, and
 * edge cases.
 */
@Slf4j
@Tag("audio")
class FmodDspClockTest {

    private FmodSystemManager systemManager;
    private FmodAudioLoadingManager loadingManager;
    private FmodPlaybackManager playbackManager;
    private FmodSystemStateManager stateManager;
    private FmodHandleLifecycleManager lifecycleManager;
    private MemorySegment system;

    private static final String SAMPLE_WAV = "src/test/resources/audio/freerecall.wav";

    @BeforeEach
    void setUp() {
        stateManager = new FmodSystemStateManager();
        systemManager =
                new FmodSystemManager(
                        new FmodLibraryLoader(
                                new FmodProperties(
                                        "unpackaged", "standard", "src/main/resources/fmod/macos"),
                                new Platform()));
        lifecycleManager = new FmodHandleLifecycleManager();

        systemManager.initialize();
        system = systemManager.getSystem();

        loadingManager = new FmodAudioLoadingManager(system, stateManager, lifecycleManager);
        playbackManager = new FmodPlaybackManager(system);

        stateManager.compareAndSetState(
                FmodSystemStateManager.State.UNINITIALIZED,
                FmodSystemStateManager.State.INITIALIZING);
        stateManager.compareAndSetState(
                FmodSystemStateManager.State.INITIALIZING,
                FmodSystemStateManager.State.INITIALIZED);
    }

    @AfterEach
    void tearDown() {
        if (loadingManager != null) {
            loadingManager.releaseAll();
        }
        if (systemManager != null) {
            systemManager.shutdown();
        }
    }

    @Test
    @DisplayName("DSP clock should provide monotonically increasing values")
    @Timeout(3)
    void testDspClockMonotonic() throws Exception {
        AudioHandle audioHandle = loadingManager.loadAudio(SAMPLE_WAV);
        MemorySegment sound = loadingManager.getCurrentSound().orElse(null);
        FmodPlaybackHandle playbackHandle = playbackManager.play(sound, audioHandle);

        long previousClock = -1;

        try (Arena arena = Arena.ofConfined()) {
            for (int i = 0; i < 20; i++) {
                var dspClockRef = arena.allocate(ValueLayout.JAVA_LONG);
                var parentClockRef = arena.allocate(ValueLayout.JAVA_LONG);

                int result =
                        FmodCore_1.FMOD_Channel_GetDSPClock(
                                playbackHandle.getChannel(), dspClockRef, parentClockRef);

                assertEquals(FmodConstants.FMOD_OK, result, "DSP clock query should succeed");

                long dspClock = dspClockRef.get(ValueLayout.JAVA_LONG, 0);

                if (previousClock >= 0) {
                    assertTrue(
                            dspClock >= previousClock,
                            "DSP clock should never go backwards: "
                                    + previousClock
                                    + " -> "
                                    + dspClock);
                }
                previousClock = dspClock;

                Thread.sleep(10);
            }
        }

        playbackManager.stop();
    }

    @Test
    @DisplayName("DSP clock should be more accurate than channel position")
    @Timeout(3)
    void testDspClockVsChannelPosition() throws Exception {
        AudioHandle audioHandle = loadingManager.loadAudio(SAMPLE_WAV);
        MemorySegment sound = loadingManager.getCurrentSound().orElse(null);
        FmodPlaybackHandle playbackHandle = playbackManager.play(sound, audioHandle);

        // Let playback run briefly to establish buffering
        Thread.sleep(100);

        try (Arena arena = Arena.ofConfined()) {
            var dspClockRef = arena.allocate(ValueLayout.JAVA_LONG);
            var parentClockRef = arena.allocate(ValueLayout.JAVA_LONG);
            var channelPosRef = arena.allocate(ValueLayout.JAVA_INT);
            var sampleRateRef = arena.allocate(ValueLayout.JAVA_INT);

            // Get system sample rate
            int sampleRateResult =
                    FmodCore.FMOD_System_GetSoftwareFormat(
                            system, sampleRateRef, MemorySegment.NULL, MemorySegment.NULL);
            assertEquals(FmodConstants.FMOD_OK, sampleRateResult, "Get sample rate should succeed");
            int sampleRate = sampleRateRef.get(ValueLayout.JAVA_INT, 0);

            // Get both positions
            int result1 =
                    FmodCore_1.FMOD_Channel_GetDSPClock(
                            playbackHandle.getChannel(), dspClockRef, parentClockRef);
            int result2 =
                    FmodCore.FMOD_Channel_GetPosition(
                            playbackHandle.getChannel(),
                            channelPosRef,
                            FmodConstants.FMOD_TIMEUNIT_PCM);

            assertEquals(FmodConstants.FMOD_OK, result1, "DSP clock should succeed");
            assertEquals(FmodConstants.FMOD_OK, result2, "Channel position should succeed");

            long dspClock = dspClockRef.get(ValueLayout.JAVA_LONG, 0);
            long channelPos = channelPosRef.get(ValueLayout.JAVA_INT, 0) & 0xFFFFFFFFL;

            // DSP clock and channel position may differ due to buffering
            // DSP clock represents what's actually playing through speakers
            // Channel position represents what's been decoded
            log.info(
                    "DSP clock: {}, Channel position: {}, Difference: {}, Sample rate: {}",
                    dspClock,
                    channelPos,
                    Math.abs(channelPos - dspClock),
                    sampleRate);

            assertTrue(dspClock >= 0, "DSP clock should be non-negative");
            assertTrue(channelPos >= 0, "Channel position should be non-negative");

            // The difference represents timing variations
            // DSP clock may be ahead or behind depending on buffering state
            long difference = Math.abs(channelPos - dspClock);
            // Typical audio buffer latency is 10-50ms
            long maxDifferenceSamples = (sampleRate * 100) / 1000; // 100ms in samples
            assertTrue(
                    difference < maxDifferenceSamples,
                    String.format(
                            "Difference should be less than 100ms worth of samples (<%d samples at"
                                    + " %dHz, actual: %d)",
                            maxDifferenceSamples, sampleRate, difference));
        }

        playbackManager.stop();
    }

    @Test
    @DisplayName("DSP clock should handle range playback end detection correctly")
    @Timeout(3)
    void testDspClockRangeEndDetection() throws Exception {
        AudioHandle audioHandle = loadingManager.loadAudio(SAMPLE_WAV);
        MemorySegment sound = loadingManager.getCurrentSound().orElse(null);

        // Play a specific range
        long startFrame = 1000;
        long endFrame = 5000;
        FmodPlaybackHandle playbackHandle =
                playbackManager.playRange(sound, audioHandle, startFrame, endFrame, false);

        // Monitor until we approach the end
        boolean reachedEnd = false;
        long lastValidClock = 0;

        try (Arena arena = Arena.ofConfined()) {
            for (int i = 0; i < 100; i++) {
                var dspClockRef = arena.allocate(ValueLayout.JAVA_LONG);
                var parentClockRef = arena.allocate(ValueLayout.JAVA_LONG);

                int result =
                        FmodCore_1.FMOD_Channel_GetDSPClock(
                                playbackHandle.getChannel(), dspClockRef, parentClockRef);

                if (result == FmodConstants.FMOD_ERR_INVALID_HANDLE) {
                    // Channel was released - playback ended
                    reachedEnd = true;
                    break;
                } else if (result == FmodConstants.FMOD_OK) {
                    long dspClock = dspClockRef.get(ValueLayout.JAVA_LONG, 0);
                    lastValidClock = dspClock;

                    // Check if we're near the end
                    if (dspClock >= endFrame - 1000) {
                        log.info(
                                "Approaching end: DSP clock = {}, end frame = {}",
                                dspClock,
                                endFrame);
                    }

                    if (dspClock >= endFrame) {
                        reachedEnd = true;
                        break;
                    }
                }

                Thread.sleep(50);
            }
        }

        assertTrue(reachedEnd, "Should detect end of range playback");
        log.info("Last valid DSP clock position: {}", lastValidClock);

        // Clean up
        playbackManager.stop();
    }

    @Test
    @DisplayName("DSP clock should handle paused state correctly")
    @Timeout(3)
    void testDspClockWhilePaused() throws Exception {
        AudioHandle audioHandle = loadingManager.loadAudio(SAMPLE_WAV);
        MemorySegment sound = loadingManager.getCurrentSound().orElse(null);
        FmodPlaybackHandle playbackHandle = playbackManager.play(sound, audioHandle);

        // Let it play briefly
        Thread.sleep(100);

        long clockBeforePause;
        long clockDuringPause1;
        long clockDuringPause2;
        long clockAfterResume;

        try (Arena arena = Arena.ofConfined()) {
            var dspClockRef = arena.allocate(ValueLayout.JAVA_LONG);
            var parentClockRef = arena.allocate(ValueLayout.JAVA_LONG);

            // Get clock before pause
            FmodCore_1.FMOD_Channel_GetDSPClock(
                    playbackHandle.getChannel(), dspClockRef, parentClockRef);
            clockBeforePause = dspClockRef.get(ValueLayout.JAVA_LONG, 0);

            // Pause
            playbackManager.pause();
            Thread.sleep(50);

            // Get clock while paused
            FmodCore_1.FMOD_Channel_GetDSPClock(
                    playbackHandle.getChannel(), dspClockRef, parentClockRef);
            clockDuringPause1 = dspClockRef.get(ValueLayout.JAVA_LONG, 0);

            Thread.sleep(100);

            // Get clock again while still paused
            FmodCore_1.FMOD_Channel_GetDSPClock(
                    playbackHandle.getChannel(), dspClockRef, parentClockRef);
            clockDuringPause2 = dspClockRef.get(ValueLayout.JAVA_LONG, 0);

            // Resume
            playbackManager.resume();
            Thread.sleep(100);

            // Get clock after resume
            FmodCore_1.FMOD_Channel_GetDSPClock(
                    playbackHandle.getChannel(), dspClockRef, parentClockRef);
            clockAfterResume = dspClockRef.get(ValueLayout.JAVA_LONG, 0);
        }

        // DSP clock should freeze while paused
        assertEquals(
                clockDuringPause1, clockDuringPause2, "DSP clock should not advance while paused");

        // DSP clock should resume advancing after unpause
        assertTrue(clockAfterResume > clockDuringPause2, "DSP clock should advance after resume");

        log.info(
                "Clock progression: before pause={}, during={}, after resume={}",
                clockBeforePause,
                clockDuringPause1,
                clockAfterResume);

        playbackManager.stop();
    }

    @Test
    @DisplayName("DSP clock should handle seek operations correctly")
    @Timeout(3)
    void testDspClockAfterSeek() throws Exception {
        AudioHandle audioHandle = loadingManager.loadAudio(SAMPLE_WAV);
        MemorySegment sound = loadingManager.getCurrentSound().orElse(null);
        FmodPlaybackHandle playbackHandle = playbackManager.play(sound, audioHandle);

        Thread.sleep(100);

        try (Arena arena = Arena.ofConfined()) {
            var dspClockRef = arena.allocate(ValueLayout.JAVA_LONG);
            var parentClockRef = arena.allocate(ValueLayout.JAVA_LONG);

            // Get initial position
            FmodCore_1.FMOD_Channel_GetDSPClock(
                    playbackHandle.getChannel(), dspClockRef, parentClockRef);
            long clockBeforeSeek = dspClockRef.get(ValueLayout.JAVA_LONG, 0);

            // Seek forward
            long seekTarget = 10000;
            playbackManager.seek(seekTarget);

            // Wait for seek to take effect
            Thread.sleep(50);

            // Get position after seek
            FmodCore_1.FMOD_Channel_GetDSPClock(
                    playbackHandle.getChannel(), dspClockRef, parentClockRef);
            long clockAfterSeek = dspClockRef.get(ValueLayout.JAVA_LONG, 0);

            // DSP clock should reflect the seek (within reasonable tolerance)
            long difference = Math.abs(clockAfterSeek - seekTarget);
            assertTrue(
                    difference < 5000,
                    String.format(
                            "DSP clock after seek should be close to target. Target: %d, Actual:"
                                    + " %d, Difference: %d",
                            seekTarget, clockAfterSeek, difference));

            log.info(
                    "Seek test: before={}, target={}, after={}, difference={}",
                    clockBeforeSeek,
                    seekTarget,
                    clockAfterSeek,
                    difference);
        }

        playbackManager.stop();
    }
}
