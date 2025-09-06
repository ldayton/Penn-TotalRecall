package app;

import a2.AudioEngine;
import a2.AudioHandle;
import a2.PlaybackHandle;
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
        AudioEngine audioEngine = null;
        AudioHandle audioHandle = null;
        PlaybackHandle playbackHandle = null;
        try {
            logger.warn("🧪 Audio Integration Test Starting");
            logger.info("=================================");

            // Test 0: Verify test audio file exists
            java.io.File audioFile = new java.io.File(TEST_AUDIO_FILE);
            if (!audioFile.exists()) {
                logger.error("❌ Test audio file not found: {}", audioFile.getAbsolutePath());
                return false;
            }
            logger.info("✅ Test audio file found: {}", audioFile.getName());

            // Test 1: Audio engine initialization
            logger.info("Testing audio engine initialization...");
            // Initialize Guice for integration tests
            app.di.GuiceBootstrap.create();
            audioEngine = app.di.GuiceBootstrap.getInjectedInstance(AudioEngine.class);
            if (audioEngine == null) {
                logger.error("❌ AudioEngine not available via dependency injection");
                logger.error("Integration tests require application to be initialized with Guice");
                return false;
            }
            logger.warn("✅ Audio engine initialized successfully");

            // Test 2: Audio file loading
            logger.info("Testing audio file loading...");
            audioHandle = audioEngine.loadAudio(TEST_AUDIO_FILE);
            if (audioHandle == null) {
                logger.error("❌ Failed to load audio file");
                return false;
            }
            logger.warn("✅ Audio file loaded successfully");

            // Test 3: Get metadata
            var metadata = audioEngine.getMetadata(audioHandle);
            logger.info(
                    "✅ Audio metadata: {} Hz, {} channels, {} frames",
                    metadata.sampleRate(),
                    metadata.channelCount(),
                    metadata.frameCount());

            // Test 4: Start playback
            logger.info("Testing audio playback...");
            playbackHandle = audioEngine.play(audioHandle);
            if (playbackHandle == null) {
                logger.error("❌ Failed to start playback");
                return false;
            }
            logger.warn("✅ Audio playback started successfully");

            // Test 5: Verify playback is running
            if (!audioEngine.isPlaying(playbackHandle)) {
                logger.error("❌ Playback not running after start");
                return false;
            }
            logger.info("✅ Playback confirmed running");

            // Test 6: Wait for playback duration
            logger.info("Playing audio for {} ms...", PLAYBACK_DURATION_MS);
            Thread.sleep(PLAYBACK_DURATION_MS);

            // Test 7: Stop playback and cleanup
            logger.info("Testing playback stop and cleanup...");
            audioEngine.stop(playbackHandle);

            // Brief wait for cleanup
            Thread.sleep(CLEANUP_WAIT_MS);

            if (audioEngine.isPlaying(playbackHandle)) {
                logger.error("❌ Playback still running after stop");
                return false;
            }
            playbackHandle = null;
            logger.info("✅ Playback stopped successfully");

            logger.warn("✅ All audio integration tests passed");
            return true;

        } catch (Exception e) {
            logger.error("❌ Audio integration test failed: {}", e.getMessage(), e);
            return false;
        } finally {
            // Ensure cleanup even if test fails
            if (audioEngine != null) {
                try {
                    if (playbackHandle != null) {
                        audioEngine.stop(playbackHandle);
                    }
                    audioEngine.close();
                } catch (Exception ignored) {
                    // Ignore cleanup errors
                }
            }
        }
    }

    /** Exit the application with appropriate status code based on test result. */
    public static void exitWithTestResult(boolean testPassed) {
        if (testPassed) {
            logger.warn("🎉 Integration test completed successfully");
            System.exit(0);
        } else {
            logger.error("💥 Integration test failed");
            System.exit(1);
        }
    }
}
