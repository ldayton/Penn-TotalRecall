package env;

import static org.junit.jupiter.api.Assertions.*;

import env.AudioSystemManager.FmodLibraryType;
import env.AudioSystemManager.LibraryLoadingMode;
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
        LibraryLoadingMode mode = manager.getFmodLoadingMode();

        // In test environment, should be UNPACKAGED (running from source)
        assertEquals(
                LibraryLoadingMode.UNPACKAGED,
                mode,
                "AudioSystemManager should return UNPACKAGED in test environment");
    }

    @Test
    @DisplayName("AudioSystemManager provides correct FMOD library type from configuration")
    void testFmodLibraryTypeFromConfiguration() {
        AudioSystemManager manager = new AudioSystemManager(config, platform);

        // Test that AudioSystemManager correctly reads configuration
        FmodLibraryType type = manager.getFmodLibraryType();

        // In test environment, should be LOGGING (set via system property)
        assertEquals(
                FmodLibraryType.LOGGING,
                type,
                "AudioSystemManager should return LOGGING in test environment");
    }

    @Test
    @DisplayName("AudioSystemManager correctly detects audio hardware availability")
    void testAudioHardwareAvailability() {
        AudioSystemManager manager = new AudioSystemManager(config, platform);

        // Test that AudioSystemManager correctly reads configuration
        boolean available = manager.isAudioHardwareAvailable();

        // Default should be true
        assertTrue(
                available,
                "AudioSystemManager should return true for audio hardware availability by default");
    }
}
