package audio;

import static org.junit.jupiter.api.Assertions.*;

import env.AppConfig;
import env.AudioSystemManager;
import env.AudioSystemManager.FmodLibraryType;
import env.AudioSystemManager.LibraryLoadingMode;
import env.Platform;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for FMOD configuration and cross-platform support in Environment and AppConfig. */
class FmodConfigurationTest {

    private final Platform platform = new Platform();
    private final AppConfig config = new AppConfig(platform);
    private final AudioSystemManager audioManager = new AudioSystemManager(config, platform);

    @Test
    @DisplayName("AudioSystemManager provides correct FMOD library filenames for each platform")
    void testFmodLibraryFilenames() {
        assertNotNull(audioManager, "AudioSystemManager instance should not be null");

        // Test that we get the correct filenames for each combination
        String standardMac = audioManager.getFmodLibraryFilename(FmodLibraryType.STANDARD);
        String loggingMac = audioManager.getFmodLibraryFilename(FmodLibraryType.LOGGING);

        // Should get platform-appropriate filenames
        // Note: This test runs on whatever platform it's executed on
        assertNotNull(standardMac, "Standard library filename should not be null");
        assertNotNull(loggingMac, "Logging library filename should not be null");
        assertNotEquals(standardMac, loggingMac);

        // Logging version should contain 'L'
        assertTrue(loggingMac.contains("L"));
        assertFalse(standardMac.contains("L"));
    }

    @Test
    @DisplayName("AudioSystemManager provides FMOD loading mode from environment configuration")
    void testFmodLoadingModeFromEnvironment() {

        // In development environment, should be UNPACKAGED
        // In CI environment, should also be UNPACKAGED (both run from source)
        // Only production packages use PACKAGED
        LibraryLoadingMode mode = audioManager.getFmodLoadingMode();
        assertEquals(LibraryLoadingMode.UNPACKAGED, mode);
    }

    @Test
    @DisplayName("Environment provides valid library paths for current platform")
    void testFmodLibraryPaths() {

        String standardPath = audioManager.getFmodLibraryDevelopmentPath(FmodLibraryType.STANDARD);
        String loggingPath = audioManager.getFmodLibraryDevelopmentPath(FmodLibraryType.LOGGING);

        assertNotNull(standardPath);
        assertNotNull(loggingPath);
        assertNotEquals(standardPath, loggingPath);

        // Paths should contain platform directory
        Platform.PlatformType platformType = platform.detect();
        String expectedPlatformDir =
                switch (platformType) {
                    case MACOS -> "macos";
                    case LINUX -> "linux";
                    case WINDOWS -> "windows";
                };

        assertTrue(standardPath.contains(expectedPlatformDir));
        assertTrue(loggingPath.contains(expectedPlatformDir));

        // Logging path should contain 'L'
        assertTrue(loggingPath.contains("L"));
        assertFalse(standardPath.contains("L"));
    }
}
