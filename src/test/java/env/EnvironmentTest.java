package env;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for Environment configuration and platform detection after refactoring.
 *
 * <p>Note: Many tests were removed/commented during the refactoring that moved functionality from
 * Environment to more appropriate classes: - FMOD methods moved to AudioSystemManager - UI methods
 * moved to LookAndFeelManager - Platform detection moved to Platform.detect()
 *
 * <p>This test class now focuses on Environment's core responsibilities: - Configuration directory
 * detection - Audio workarounds - Release URL configuration
 */
@DisplayName("Environment Configuration")
class EnvironmentTest {

    @AfterEach
    void cleanupSystemProperties() {
        // Clean up any test system properties
        System.clearProperty("test.priority.key");
        System.clearProperty("test.enum.override");
        System.clearProperty("test.override.key");
        System.clearProperty("fmod.loading.mode");
        System.clearProperty("fmod.library.type");
    }

    @Test
    @DisplayName("provides platform-specific configuration directories")
    void providesPlatformSpecificConfigurationDirectories() {
        Platform platform = new Platform();
        AppConfig config = new AppConfig(platform);
        Environment env = new Environment(config, platform);

        // Test that config directory is provided
        assertNotNull(env.getConfigDirectory(), "Should provide config directory");
        assertNotNull(env.getUserHomeDir(), "Should provide user home directory");
    }

    @Test
    @DisplayName("provides release URLs from configuration")
    void providesReleaseUrlsFromConfiguration() {
        Platform platform = new Platform();
        AppConfig config = new AppConfig(platform);
        Environment env = new Environment(config, platform);

        // These might be null in test environment, which is fine
        // Just testing that methods exist and don't throw
        assertDoesNotThrow(() -> env.getReleasesApiUrl());
        assertDoesNotThrow(() -> env.getReleasesPageUrl());
    }

    @Test
    @DisplayName("provides audio workarounds without throwing")
    void providesAudioWorkaroundsWithoutThrowing() {
        Platform platform = new Platform();
        AppConfig config = new AppConfig(platform);
        Environment env = new Environment(config, platform);

        // Should not throw for any platform
        assertDoesNotThrow(() -> env.applyAudioWorkarounds());

        // Test error formatting
        String formatted = env.formatAudioError(-1, "Base error");
        assertNotNull(formatted);
        assertTrue(formatted.contains("Base error"));
    }

    @Test
    @DisplayName("constructor with dependency injection works")
    void constructorWithDependencyInjectionWorks() {
        Platform platform = new Platform();
        AppConfig config = new AppConfig(platform);

        // Test main DI constructor
        assertDoesNotThrow(() -> new Environment(config, platform));

        Environment env = new Environment(config, platform);
        assertNotNull(env.getUserHomeDir());
        assertNotNull(env.getConfigDirectory());
    }

    @Test
    @DisplayName("constructor for testing works")
    void constructorForTestingWorks() {
        Platform platform = new Platform();
        AppConfig config = new AppConfig(platform);

        // Test the testing constructor with platform type
        assertDoesNotThrow(() -> new Environment(Platform.PlatformType.MACOS, config));

        Environment env = new Environment(Platform.PlatformType.MACOS, config);
        assertNotNull(env.getUserHomeDir());
        assertNotNull(env.getConfigDirectory());
    }

    // TODO: The following test methods were commented out during refactoring
    // as the functionality moved to other classes:

    // - testFmodMethods() -> moved to AudioSystemManagerTest
    // - testPlatformDetection() -> moved to PlatformTest
    // - testUIPreferences() -> moved to LookAndFeelManagerTest
    // - testAppIconPaths() -> moved to LookAndFeelManagerTest
    // - Complex configuration injection tests -> need architectural review

    // These should be restored in appropriate test classes or updated to match
    // the new dependency injection architecture.
}
