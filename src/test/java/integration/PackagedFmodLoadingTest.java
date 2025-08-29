package integration;

import static org.junit.jupiter.api.Assertions.*;

import annotations.Packaging;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.NonNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration tests for packaged FMOD loading in macOS .app bundles.
 *
 * <p>These tests verify that the built application can successfully load FMOD libraries and perform
 * audio operations when running from a packaged .app bundle. This validates the complete deployment
 * pipeline including library path resolution, native dependency loading, and audio system
 * initialization.
 *
 * <p>Requires: macOS .app bundle built via {@code ./gradlew packageMacApp}
 *
 * <p>Run with: {@code ./gradlew packageTest}
 */
@DisplayName("Packaged FMOD Loading Integration Tests")
class PackagedFmodLoadingTest {
    private static final Logger logger = LoggerFactory.getLogger(PackagedFmodLoadingTest.class);

    private static final String PROGRAM_NAME =
            new env.AppConfig().getProperty(env.AppConfig.APP_NAME_KEY);
    private static final Path APP_BUNDLE =
            Paths.get("build/packaging/mac/" + PROGRAM_NAME + ".app");
    private static final Path APP_EXECUTABLE = APP_BUNDLE.resolve("Contents/MacOS/" + PROGRAM_NAME);
    private static final Path FMOD_LIBRARY =
            APP_BUNDLE.resolve("Contents/Frameworks/libfmod.dylib");

    @Test
    @Packaging
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    @DisplayName("packaged app bundle can load FMOD and run audio integration tests")
    void packagedAppBundleCanLoadFmodAndRunAudioIntegrationTests() throws Exception {
        logger.info("🧪 Testing packaged FMOD loading in .app bundle");
        logger.info("📦 App bundle: {}", APP_BUNDLE.getFileName());

        // Verify build system provided required artifacts
        assertAppBundleExists();
        assertFmodLibraryExists();
        assertExecutableExists();

        // Execute the packaged application in integration test mode
        var result = executeIntegrationTest();

        // Verify test execution results
        assertEquals(
                0,
                result.exitCode(),
                "Integration test should pass (exit code 0), but got: "
                        + result.exitCode()
                        + "\nSTDOUT:\n"
                        + String.join("\n", result.stdout())
                        + "\nSTDERR:\n"
                        + String.join("\n", result.stderr()));

        // Verify specific test phases completed successfully
        assertTestPhasesCompleted(result.stdout());

        logger.info("✅ Packaged FMOD loading test completed successfully");
    }

    private void assertAppBundleExists() {
        assertTrue(
                Files.exists(APP_BUNDLE),
                "App bundle must exist: "
                        + APP_BUNDLE
                        + "\nRun './gradlew packageMacApp' to build the .app bundle");
        assertTrue(Files.isDirectory(APP_BUNDLE), "App bundle must be a directory: " + APP_BUNDLE);
        logger.info("✅ App bundle exists: {}", APP_BUNDLE.getFileName());
    }

    private void assertFmodLibraryExists() {
        assertTrue(
                Files.exists(FMOD_LIBRARY),
                "FMOD library must exist in app bundle: "
                        + FMOD_LIBRARY
                        + "\nVerify packaging process copies FMOD libraries correctly");
        logger.info("✅ FMOD library exists: {}", FMOD_LIBRARY.getFileName());
    }

    private void assertExecutableExists() {
        assertTrue(Files.exists(APP_EXECUTABLE), "App executable must exist: " + APP_EXECUTABLE);
        assertTrue(
                Files.isExecutable(APP_EXECUTABLE),
                "App executable must be executable: " + APP_EXECUTABLE);
        logger.info("✅ App executable exists and is executable: {}", APP_EXECUTABLE.getFileName());
    }

    private ProcessResult executeIntegrationTest() throws Exception {
        logger.info("🚀 Executing integration test: {} --integration-test", APP_EXECUTABLE);

        var processBuilder = new ProcessBuilder(APP_EXECUTABLE.toString(), "--integration-test");
        processBuilder.redirectErrorStream(false);

        var process = processBuilder.start();

        // Capture output streams
        var stdout = captureStream(process.getInputStream());
        var stderr = captureStream(process.getErrorStream());

        // Wait for completion with timeout
        boolean completed = process.waitFor(12, TimeUnit.SECONDS);
        if (!completed) {
            process.destroyForcibly();
            fail("Integration test process timed out after 12 seconds");
        }

        int exitCode = process.exitValue();
        var stdoutLines = stdout.get(2, TimeUnit.SECONDS);
        var stderrLines = stderr.get(2, TimeUnit.SECONDS);

        logger.info("Integration test completed with exit code: {}", exitCode);
        if (!stdoutLines.isEmpty()) {
            logger.info("STDOUT:\n{}", String.join("\n", stdoutLines));
        }
        if (!stderrLines.isEmpty()) {
            logger.info("STDERR:\n{}", String.join("\n", stderrLines));
        }

        return new ProcessResult(exitCode, stdoutLines, stderrLines);
    }

    private java.util.concurrent.Future<List<String>> captureStream(
            @NonNull java.io.InputStream stream) {
        return java.util.concurrent.CompletableFuture.supplyAsync(
                () -> {
                    try (var reader = new BufferedReader(new InputStreamReader(stream))) {
                        return reader.lines().toList();
                    } catch (IOException e) {
                        logger.warn("Failed to capture stream: {}", e.getMessage());
                        return List.of();
                    }
                });
    }

    private void assertTestPhasesCompleted(@NonNull List<String> stdout) {
        var output = String.join("\n", stdout);

        // Verify key test phases from AudioIntegrationMode completed
        assertTrue(
                output.contains("Audio Integration Test Starting")
                        || output.contains("🧪 Audio Integration Test Starting"),
                "Test should start audio integration phase");

        assertTrue(
                output.contains("✅ FMOD library loaded successfully")
                        || output.contains("FMOD library loaded successfully"),
                "Test should successfully load FMOD library");

        assertTrue(
                output.contains("✅ Audio playback started successfully")
                        || output.contains("Audio playback started successfully"),
                "Test should successfully start audio playback");

        assertTrue(
                output.contains("✅ All audio integration tests passed")
                        || output.contains("All audio integration tests passed")
                        || output.contains("Integration test completed successfully"),
                "Test should complete all audio integration phases");
    }

    private record ProcessResult(int exitCode, List<String> stdout, List<String> stderr) {}
}
