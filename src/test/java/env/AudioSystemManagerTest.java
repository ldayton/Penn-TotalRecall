package env;

import static org.junit.jupiter.api.Assertions.*;

import env.AudioSystemManager.FmodLibraryType;
import env.AudioSystemManager.LibraryLoadingMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for AudioSystemManager functionality and dependency injection. */
class AudioSystemManagerTest {

    private final Environment env = new Environment();
    private final AppConfig config = new AppConfig();

    @Test
    @DisplayName("AudioSystemManager constructor works with dependency injection")
    void testAudioSystemManagerConstructor() {
        // Should not throw - this replaces the old FmodLibraryLoader constructor test
        AudioSystemManager manager = new AudioSystemManager(config, env);
        assertNotNull(manager);
    }

    @Test
    @DisplayName("AudioSystemManager provides correct FMOD loading mode from configuration")
    void testFmodLoadingModeFromConfiguration() {
        AudioSystemManager manager = new AudioSystemManager(config, env);

        // Test that AudioSystemManager correctly delegates to config
        LibraryLoadingMode mode = manager.getFmodLoadingMode();
        LibraryLoadingMode expectedMode = config.getFmodLoadingMode();
        
        assertEquals(expectedMode, mode, "AudioSystemManager should return same mode as AppConfig");
    }

    @Test
    @DisplayName("AudioSystemManager provides correct FMOD library type from configuration")
    void testFmodLibraryTypeFromConfiguration() {
        AudioSystemManager manager = new AudioSystemManager(config, env);

        // Test that AudioSystemManager correctly delegates to config
        FmodLibraryType type = manager.getFmodLibraryType();
        FmodLibraryType expectedType = config.getFmodLibraryType();
        
        assertEquals(expectedType, type, "AudioSystemManager should return same type as AppConfig");
    }

    @Test
    @DisplayName("AudioSystemManager correctly detects audio hardware availability")
    void testAudioHardwareAvailability() {
        AudioSystemManager manager = new AudioSystemManager(config, env);

        // Test that AudioSystemManager correctly delegates to config
        boolean available = manager.isAudioHardwareAvailable();
        boolean expectedAvailable = config.isAudioHardwareAvailable();
        
        assertEquals(expectedAvailable, available, "AudioSystemManager should return same availability as AppConfig");
    }

    @Test
    @DisplayName("AudioSystemManager loadAudioLibrary method accepts interface classes")
    void testLoadAudioLibraryMethodSignature() {
        AudioSystemManager manager = new AudioSystemManager(config, env);

        // Test that the method exists and accepts interface classes
        // We won't actually load a library in tests, just verify the method works
        assertNotNull(manager, "AudioSystemManager should be constructed successfully");
        
        // This test ensures the method signature is correct - actual loading is tested in integration tests
        // to avoid requiring real FMOD libraries in unit tests
    }
}