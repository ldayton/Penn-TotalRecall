package control;

import audio.FmodCore;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Audio integration test mode for verifying FMOD loading and playback in packaged environments.
 *
 * <p>This class provides a headless integration test that:
 *
 * <ul>
 *   <li>Loads FMOD through normal application code paths
 *   <li>Loads a test audio file
 *   <li>Plays audio for 1 second
 *   <li>Exits with appropriate status code
 * </ul>
 */
public class AudioIntegrationMode {
    private static final Logger logger = LoggerFactory.getLogger(AudioIntegrationMode.class);
    private static final int TIMEOUT_SECONDS = 10; // Maximum time for entire test

    // Test configuration constants
    private static final int PLAYBACK_DURATION_MS = 1000;
    private static final int CLEANUP_WAIT_MS = 100;
    private static final String TEST_AUDIO_FILE = "packaging/samples/sample.wav";
    private static final int EXPECTED_SAMPLE_RATE = 44100;

    /**
     * Run the audio integration test with timeout protection.
     *
     * @return true if test passed, false if failed
     */
    public static boolean runTest() {
        try {
            // Run the test with a timeout to prevent hanging
            CompletableFuture<Boolean> testFuture =
                    CompletableFuture.supplyAsync(AudioIntegrationMode::runTestInternal);
            return testFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            logger.error("❌ Integration test timed out after {} seconds", TIMEOUT_SECONDS);
            return false;
        } catch (Throwable t) {
            logger.error("❌ Integration test failed with throwable: " + t.getMessage(), t);
            return false;
        }
    }

    /**
     * Internal test implementation without timeout handling.
     *
     * @return true if test passed, false if failed
     */
    private static boolean runTestInternal() {
        FmodCore core = null;
        try {
            logger.info("🧪 Audio Integration Test Starting");
            logger.info("=================================");

            // Test 0: Verify test audio file exists
            java.io.File audioFile = new java.io.File(TEST_AUDIO_FILE);
            if (!audioFile.exists()) {
                logger.error("❌ Test audio file not found: {}", audioFile.getAbsolutePath());
                return false;
            }
            logger.info("✅ Test audio file found: {}", audioFile.getName());

            // Test 1: FMOD library loading
            logger.info("Testing FMOD library loading...");
            // Initialize Guice for integration tests
            di.GuiceBootstrap.create();
            core = di.GuiceBootstrap.getInjectedInstance(FmodCore.class);
            if (core == null) {
                logger.error("❌ FmodCore not available via dependency injection");
                logger.error("Integration tests require application to be initialized with Guice");
                return false;
            }
            logger.info("✅ FMOD library loaded successfully");

            // Test 2: Audio file loading and playback
            logger.info("Testing audio file loading and playback...");

            // Try to start playback for 1 second (frames = duration_ms * sample_rate / 1000)
            long playbackFrames = (long) PLAYBACK_DURATION_MS * EXPECTED_SAMPLE_RATE / 1000;

            int result = core.startPlayback(TEST_AUDIO_FILE, 0, playbackFrames);
            if (result != 0) {
                logger.error("❌ Failed to start playback. Error code: {}", result);
                return false;
            }
            logger.info("✅ Audio playback started successfully");

            // Test 3: Verify playback is running
            if (!core.playbackInProgress()) {
                logger.error("❌ Playback not in progress after start");
                return false;
            }
            logger.info("✅ Playback confirmed running");

            // Test 4: Wait for playback duration
            logger.info("Playing audio for {} ms...", PLAYBACK_DURATION_MS);
            Thread.sleep(PLAYBACK_DURATION_MS);

            // Test 5: Stop playback and cleanup
            logger.info("Testing playback stop and cleanup...");
            core.stopPlayback();

            // Brief wait for cleanup
            Thread.sleep(CLEANUP_WAIT_MS);

            if (core.playbackInProgress()) {
                logger.error("❌ Playback still in progress after stop");
                return false;
            }
            logger.info("✅ Playback stopped successfully");

            logger.info("✅ All audio integration tests passed");
            return true;

        } catch (Exception e) {
            logger.error("❌ Audio integration test failed: {}", e.getMessage(), e);
            return false;
        } finally {
            // Ensure cleanup even if test fails
            if (core != null) {
                try {
                    core.stopPlayback();
                } catch (Exception ignored) {
                    // Ignore cleanup errors
                }
            }
        }
    }

    /** Exit the application with appropriate status code based on test result. */
    public static void exitWithTestResult(boolean testPassed) {
        if (testPassed) {
            logger.info("🎉 Integration test completed successfully");
            System.exit(0);
        } else {
            logger.error("💥 Integration test failed");
            System.exit(1);
        }
    }
}
