package a2.fmod;

import static org.junit.jupiter.api.Assertions.*;

import a2.fmod.AudioSystemManager.LibraryLoadingMode;
import a2.fmod.AudioSystemManager.LibraryType;
import env.AppConfig;
import env.Platform;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for AudioSystemManager functionality and dependency injection. */
class AudioSystemManagerTest {

    private final Platform platform = new Platform();
    private final AppConfig config = new AppConfig();

    @Test
    @DisplayName("AudioSystemManager provides correct FMOD loading mode from configuration")
    void testFmodLoadingModeFromConfiguration() {
        AudioSystemManager manager = new AudioSystemManager(config, platform);

        // Test that AudioSystemManager correctly reads configuration
        LibraryLoadingMode mode = manager.getLoadingMode();

        // In test environment, should be UNPACKAGED (running from source)
        assertEquals(
                LibraryLoadingMode.UNPACKAGED,
                mode,
                "AudioSystemManager should return UNPACKAGED in test environment");
    }

    @Test
    @DisplayName("AudioSystemManager provides correct FMOD library type from configuration")
    void testLibraryTypeFromConfiguration() {
        AudioSystemManager manager = new AudioSystemManager(config, platform);

        // Test that AudioSystemManager correctly reads configuration
        LibraryType type = manager.getLibraryType();

        // In test environment, should be LOGGING (set via system property)
        assertEquals(
                LibraryType.LOGGING,
                type,
                "AudioSystemManager should return LOGGING in test environment");
    }

    @Test
    @DisplayName("AudioSystemManager correctly detects audio hardware availability")
    void testAudioHardwareAvailability() {
        AudioSystemManager manager = new AudioSystemManager(config, platform);

        // Test that AudioSystemManager correctly reads configuration
        boolean available = manager.isAudioHardwareAvailable();

        // Expected value should match what's actually configured in the AppConfig
        boolean expected = config.getBooleanProperty("audio.hardware.available", true);

        assertEquals(
                expected,
                available,
                "AudioSystemManager should return the configured audio hardware availability"
                        + " value");
    }
}
