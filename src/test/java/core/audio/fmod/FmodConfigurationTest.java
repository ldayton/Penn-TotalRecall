package core.audio.fmod;

import static org.junit.jupiter.api.Assertions.*;

import core.audio.fmod.FmodLibraryLoader.LibraryLoadingMode;
import core.audio.fmod.FmodLibraryLoader.LibraryType;
import core.env.Platform;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Tests for FMOD configuration and cross-platform support in Environment and AppConfig. */
@Tag("audio")
class FmodConfigurationTest {

    private final FmodLibraryLoader audioManager =
            new FmodLibraryLoader(
                    new FmodProperties("unpackaged", "standard"),
                    new Platform());

    @Test
    @DisplayName("FmodLibraryLoader provides correct FMOD library filenames for each platform")
    void testFmodLibraryFilenames() {
        assertNotNull(audioManager, "FmodLibraryLoader instance should not be null");

        // Test that we get the correct filenames for each combination
        String standardMac = audioManager.getLibraryFilename(LibraryType.STANDARD);
        String loggingMac = audioManager.getLibraryFilename(LibraryType.LOGGING);

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
        LibraryLoadingMode mode = audioManager.getLoadingMode();
        assertEquals(LibraryLoadingMode.UNPACKAGED, mode);
    }

    @Test
    @DisplayName("Environment provides valid library paths for current platform")
    void testFmodLibraryPaths() {

        String standardPath = audioManager.getLibraryDevelopmentPath(LibraryType.STANDARD);
        String loggingPath = audioManager.getLibraryDevelopmentPath(LibraryType.LOGGING);

        assertNotNull(standardPath);
        assertNotNull(loggingPath);
        assertNotEquals(standardPath, loggingPath);

        String expectedPlatformDir = "macos";
        assertTrue(standardPath.contains(expectedPlatformDir));
        assertTrue(loggingPath.contains(expectedPlatformDir));

        assertTrue(loggingPath.contains("L"));
        assertFalse(standardPath.contains("L"));
    }
}
