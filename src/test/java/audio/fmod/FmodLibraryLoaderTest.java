package audio.fmod;

import static org.junit.jupiter.api.Assertions.*;

import audio.fmod.FmodLibraryLoader.LibraryLoadingMode;
import audio.fmod.FmodLibraryLoader.LibraryType;
import env.AppConfig;
import env.Platform;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for FmodLibraryLoader functionality and dependency injection. */
class FmodLibraryLoaderTest {

    private final Platform platform = new Platform();
    private final AppConfig config = new AppConfig();

    @Test
    @DisplayName("FmodLibraryLoader provides correct FMOD loading mode from configuration")
    void testFmodLoadingModeFromConfiguration() {
        FmodLibraryLoader loader = new FmodLibraryLoader(config, platform);

        // Test that FmodLibraryLoader correctly reads configuration
        LibraryLoadingMode mode = loader.getLoadingMode();

        // In test environment, should be UNPACKAGED (running from source)
        assertEquals(
                LibraryLoadingMode.UNPACKAGED,
                mode,
                "FmodLibraryLoader should return UNPACKAGED in test environment");
    }

    @Test
    @DisplayName("FmodLibraryLoader provides correct FMOD library type from configuration")
    void testLibraryTypeFromConfiguration() {
        FmodLibraryLoader loader = new FmodLibraryLoader(config, platform);

        // Test that FmodLibraryLoader correctly reads configuration
        LibraryType type = loader.getLibraryType();

        // In test environment, should be LOGGING (set via system property)
        assertEquals(
                LibraryType.LOGGING,
                type,
                "FmodLibraryLoader should return LOGGING in test environment");
    }

    @Test
    @DisplayName("FmodLibraryLoader correctly detects audio hardware availability")
    void testAudioHardwareAvailability() {
        FmodLibraryLoader loader = new FmodLibraryLoader(config, platform);

        // Test that FmodLibraryLoader correctly reads configuration
        boolean available = loader.isAudioHardwareAvailable();

        // Expected value should match what's actually configured in the AppConfig
        boolean expected = config.getBooleanProperty("audio.hardware.available", true);

        assertEquals(
                expected,
                available,
                "FmodLibraryLoader should return the configured audio hardware availability"
                        + " value");
    }
}
