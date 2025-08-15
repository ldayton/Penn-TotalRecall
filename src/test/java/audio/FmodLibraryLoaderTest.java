package audio;

import static org.junit.jupiter.api.Assertions.*;

import env.AppConfig;
import env.Environment;
import env.FmodLibraryType;
import env.LibraryLoadingMode;
import env.Platform;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for FMOD library loading configuration and cross-platform support. */
class FmodLibraryLoaderTest {

    @Test
    @DisplayName("Environment provides correct FMOD library filenames for each platform")
    void testFmodLibraryFilenames() {
        Environment env = Environment.getInstance();
        assertNotNull(env, "Environment instance should not be null");

        // Test that we get the correct filenames for each combination
        String standardMac = env.getFmodLibraryFilename(FmodLibraryType.STANDARD);
        String loggingMac = env.getFmodLibraryFilename(FmodLibraryType.LOGGING);

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
    @DisplayName("AppConfig provides FMOD loading mode from environment configuration")
    void testFmodLoadingModeFromEnvironment() {
        AppConfig config = AppConfig.getInstance();

        // In development environment, should be UNPACKAGED
        // In CI environment, should also be UNPACKAGED (both run from source)
        // Only production packages use PACKAGED
        LibraryLoadingMode mode = config.getFmodLoadingMode();
        assertEquals(LibraryLoadingMode.UNPACKAGED, mode);
    }

    @Test
    @DisplayName("FmodLibraryLoader constructor works with dependency injection")
    void testFmodLibraryLoaderConstructor() {
        AppConfig config = AppConfig.getInstance();
        Environment env = Environment.getInstance();

        // Should not throw
        FmodLibraryLoader loader = new FmodLibraryLoader(config, env);
        assertNotNull(loader);

        // Default constructor should also work
        FmodLibraryLoader defaultLoader = new FmodLibraryLoader();
        assertNotNull(defaultLoader);
    }

    @Test
    @DisplayName("Environment provides valid library paths for current platform")
    void testFmodLibraryPaths() {
        Environment env = Environment.getInstance();

        String standardPath = env.getFmodLibraryDevelopmentPath(FmodLibraryType.STANDARD);
        String loggingPath = env.getFmodLibraryDevelopmentPath(FmodLibraryType.LOGGING);

        assertNotNull(standardPath);
        assertNotNull(loggingPath);
        assertNotEquals(standardPath, loggingPath);

        // Paths should contain platform directory
        Platform platform = env.getPlatform();
        String expectedPlatformDir =
                switch (platform) {
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
