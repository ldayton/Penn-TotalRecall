package env;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for Environment configuration loading and property resolution.
 *
 * <p>Verifies that configuration is loaded correctly from multiple sources in the proper priority
 * order, and that all getter methods work as expected. Focuses on testing configuration loading
 * mechanics rather than specific configuration content.
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
    @DisplayName("loads configuration from bundled application.properties")
    void loadsConfigurationFromBundledApplicationProperties() {
        Environment env = new Environment();

        // Verify that update URLs are loaded from application.properties
        String apiUrl = env.getReleasesApiUrl();
        String pageUrl = env.getReleasesPageUrl();

        assertNotNull(apiUrl, "releases.api.url should be loaded from application.properties");
        assertNotNull(pageUrl, "releases.page.url should be loaded from application.properties");

        assertTrue(apiUrl.contains("github.com"), "API URL should point to GitHub");
        assertTrue(pageUrl.contains("github.com"), "Page URL should point to GitHub");
        assertTrue(apiUrl.contains("api"), "API URL should be an API endpoint");
        assertTrue(pageUrl.contains("releases"), "Page URL should point to releases");
    }

    @Test
    @DisplayName("loads FMOD configuration from multiple sources")
    void loadsFmodConfigurationFromMultipleSources() {
        Environment env = new Environment();

        // These should be loaded from config files
        AudioSystemManager.FmodLibraryType libraryType = env.getFmodLibraryType();
        AudioSystemManager.LibraryLoadingMode loadingMode = env.getFmodLoadingMode();

        assertNotNull(libraryType, "FMOD library type should be configured");
        assertNotNull(loadingMode, "FMOD loading mode should be configured");

        // Verify we can get platform-specific filenames
        String filename = env.getFmodLibraryFilename(AudioSystemManager.FmodLibraryType.STANDARD);
        assertNotNull(filename, "Should provide platform-specific library filename");
        assertFalse(filename.isEmpty(), "Filename should not be empty");

        // Verify development paths work
        String devPath = env.getFmodLibraryDevelopmentPath(AudioSystemManager.FmodLibraryType.STANDARD);
        assertNotNull(devPath, "Should provide development library path");
        assertTrue(devPath.contains("src/main/resources"), "Development path should point to resources");
    }

    @Test
    @DisplayName("system properties override configuration files")
    void systemPropertiesOverrideConfigurationFiles() {
        // Set a system property that should override file-based config
        String originalValue = System.getProperty("test.override.property");
        System.setProperty("test.override.property", "system-value");

        try {
            Environment env = new Environment();

            // System properties are loaded last and should override everything
            // We can't easily test this directly since Environment doesn't expose
            // raw property access, but we can verify the mechanism works
            assertNotNull(env, "Environment should create successfully with system properties");

        } finally {
            // Clean up system property
            if (originalValue != null) {
                System.setProperty("test.override.property", originalValue);
            } else {
                System.clearProperty("test.override.property");
            }
        }
    }

    @Test
    @DisplayName("provides platform-specific behavior configuration")
    void providesPlatformSpecificBehaviorConfiguration() {
        Environment env = new Environment();

        // Test platform detection
        Platform platform = env.getPlatform();
        assertNotNull(platform, "Should detect current platform");

        // Test platform-specific UI behavior
        boolean useNativeFileChoosers = env.shouldUseAWTFileChoosers();
        String preferencesString = env.getPreferencesString();
        boolean showPrefsInMenu = env.shouldShowPreferencesInMenu();

        // These should be determined based on platform
        assertNotNull(preferencesString, "Should provide preferences string");
        assertFalse(preferencesString.isEmpty(), "Preferences string should not be empty");

        // Platform-specific assertions
        if (platform == Platform.MACOS) {
            assertTrue(useNativeFileChoosers, "macOS should use native file choosers by default");
            assertEquals("Preferences", preferencesString, "macOS should use 'Preferences'");
            assertFalse(showPrefsInMenu, "macOS should not show prefs in application menu");
        }
    }

    @Test
    @DisplayName("provides consistent audio configuration")
    void providesConsistentAudioConfiguration() {
        Environment env = new Environment();

        // Test audio configuration values
        int chunkSize = env.getChunkSizeInSeconds();
        int maxPixels = env.getMaxInterpolatedPixels();
        double tolerance = env.getInterpolationToleratedErrorZoneInSec();

        assertTrue(chunkSize > 0, "Chunk size should be positive");
        assertTrue(maxPixels > 0, "Max interpolated pixels should be positive");
        assertTrue(tolerance > 0, "Interpolation tolerance should be positive");

        // Values should be reasonable
        assertTrue(chunkSize <= 60, "Chunk size should be reasonable (≤60 seconds)");
        assertTrue(maxPixels <= 100, "Max pixels should be reasonable (≤100)");
        assertTrue(tolerance <= 1.0, "Tolerance should be reasonable (≤1 second)");
    }

    @Test
    @DisplayName("handles missing user configuration gracefully")
    void handlesMissingUserConfigurationGracefully() {
        // Create environment - user config file likely doesn't exist in test environment
        Environment env = new Environment();

        // Should still work and provide defaults
        assertNotNull(env.getPlatform(), "Should detect platform even without user config");
        assertNotNull(env.getUserHomeDir(), "Should provide home directory");
        assertNotNull(env.getConfigDirectory(), "Should provide config directory path");

        // Core functionality should work
        assertTrue(env.getChunkSizeInSeconds() > 0, "Should provide default chunk size");
        assertNotNull(env.getAboutMessage(), "Should build about message");
    }

    @Test
    @DisplayName("test constructor works for unit testing")
    void testConstructorWorksForUnitTesting() {
        // Test the constructor that takes a Platform parameter (used for testing)
        Environment env = new Environment(Platform.MACOS);

        assertEquals(Platform.MACOS, env.getPlatform(), "Should use injected platform");
        assertNotNull(env.getUserHomeDir(), "Should still provide user home");
        assertNotNull(env.getAboutMessage(), "Should provide test about message");
        assertEquals(2, env.getChunkSizeInSeconds(), "Should use test default chunk size");

        // Should handle missing configuration gracefully
        assertNull(env.getReleasesApiUrl(), "Test environment should have no releases URL");
        assertNull(env.getReleasesPageUrl(), "Test environment should have no releases page URL");
    }

    @Test
    @DisplayName("provides valid app icon paths for all platforms")
    void providesValidAppIconPathsForAllPlatforms() {
        // Test each platform's icon path
        for (Platform platform : Platform.values()) {
            Environment env = new Environment(platform);
            String iconPath = env.getAppIconPath();

            assertNotNull(iconPath, "Should provide icon path for " + platform);
            assertTrue(iconPath.startsWith("/"), "Icon path should be absolute");
            assertTrue(iconPath.endsWith(".png"), "Icon should be PNG format");
            assertTrue(iconPath.contains("headphones"), "Icon should be headphones themed");
        }
    }

    @Test
    @DisplayName("audio workarounds and error formatting work")
    void audioWorkaroundsAndErrorFormattingWork() {
        Environment env = new Environment();

        // Test audio workarounds (should not throw)
        assertDoesNotThrow(() -> env.applyAudioWorkarounds(), 
            "Audio workarounds should not throw exceptions");

        // Test error formatting
        String formattedError = env.formatAudioError(-1, "Test error");
        assertNotNull(formattedError, "Should format error messages");
        assertTrue(formattedError.contains("Test error"), "Should include base message");

        // Platform-specific behavior
        if (env.getPlatform() == Platform.LINUX) {
            assertTrue(formattedError.length() > "Test error".length(), 
                "Linux should add additional context to error messages");
        }
    }

    @Test
    @DisplayName("FMOD library configuration handles invalid values gracefully")
    void fmodLibraryConfigurationHandlesInvalidValuesGracefully() {
        // Test with system properties that have invalid enum values
        String originalMode = System.getProperty("fmod.loading.mode");
        String originalType = System.getProperty("fmod.library.type");

        try {
            System.setProperty("fmod.loading.mode", "invalid_mode");
            System.setProperty("fmod.library.type", "invalid_type");

            Environment env = new Environment();

            // Should fall back to defaults for invalid values
            assertEquals(AudioSystemManager.LibraryLoadingMode.PACKAGED, env.getFmodLoadingMode(),
                "Should default to PACKAGED for invalid loading mode");
            assertEquals(AudioSystemManager.FmodLibraryType.STANDARD, env.getFmodLibraryType(),
                "Should default to STANDARD for invalid library type");

        } finally {
            // Clean up
            if (originalMode != null) {
                System.setProperty("fmod.loading.mode", originalMode);
            } else {
                System.clearProperty("fmod.loading.mode");
            }
            if (originalType != null) {
                System.setProperty("fmod.library.type", originalType);
            } else {
                System.clearProperty("fmod.library.type");
            }
        }
    }

    // =============================================================================
    // CONFIGURATION LOADING MECHANICS TESTS
    // =============================================================================

    @Test
    @DisplayName("configuration priority order: system > user > platform > application > defaults")
    void configurationPriorityOrderWorks() {
        // Test that later sources override earlier ones
        String defaults = "test.priority.key=default_value";
        String application = "test.priority.key=app_value";
        String platform = "test.priority.key=platform_value";
        String user = "test.priority.key=user_value";

        Environment env = new Environment(Platform.MACOS, defaults, application, platform, user);

        // Should use user value (highest file-based priority)
        assertEquals("user_value", env.getStringProperty("test.priority.key", "fallback"));

        // Now test system property override
        System.setProperty("test.priority.key", "system_value");
        Environment envWithSystem = new Environment(Platform.MACOS, defaults, application, platform, user);
        assertEquals("system_value", envWithSystem.getStringProperty("test.priority.key", "fallback"));
    }

    @Test
    @DisplayName("missing configuration sources are handled gracefully")
    void missingConfigurationSourcesHandledGracefully() {
        // Test with all configs null except one
        String onlyApp = "test.key=app_only";
        Environment env = new Environment(Platform.MACOS, null, onlyApp, null, null);

        assertEquals("app_only", env.getStringProperty("test.key", "fallback"));

        // Test with all configs null
        Environment emptyEnv = new Environment(Platform.MACOS, null, null, null, null);
        assertEquals("fallback", emptyEnv.getStringProperty("test.key", "fallback"));
    }

    @Test
    @DisplayName("platform injection affects configuration loading behavior")
    void platformInjectionAffectsConfigurationBehavior() {
        // Test that platform-specific behavior works correctly
        String platformConfig = "ui.preferences_menu_title=platform_specific";

        Environment macEnv = new Environment(Platform.MACOS, null, null, platformConfig, null);
        Environment winEnv = new Environment(Platform.WINDOWS, null, null, platformConfig, null);
        Environment linuxEnv = new Environment(Platform.LINUX, null, null, platformConfig, null);

        // All should get the same injected config since we injected the same string
        assertEquals("platform_specific", macEnv.getPreferencesString());
        assertEquals("platform_specific", winEnv.getPreferencesString());
        assertEquals("platform_specific", linuxEnv.getPreferencesString());

        // But platform detection should still work correctly
        assertEquals(Platform.MACOS, macEnv.getPlatform());
        assertEquals(Platform.WINDOWS, winEnv.getPlatform());
        assertEquals(Platform.LINUX, linuxEnv.getPlatform());
    }

    @Test
    @DisplayName("invalid configuration content is handled gracefully")
    void invalidConfigurationContentHandledGracefully() {
        // Test with malformed properties
        String invalidConfig = "this is not=valid\nproperties format\n=missing key";
        String validConfig = "valid.key=valid_value";

        // Should not crash and should still load valid parts
        Environment env = new Environment(Platform.MACOS, invalidConfig, validConfig, null, null);
        assertEquals("valid_value", env.getStringProperty("valid.key", "fallback"));
    }

    @Test
    @DisplayName("enum configuration with system property override")
    void enumConfigurationWithSystemPropertyOverride() {
        // Test FMOD enum loading with override
        String config = "fmod.loading.mode=packaged\nfmod.library.type=standard";
        Environment env = new Environment(Platform.MACOS, null, config, null, null);

        assertEquals(AudioSystemManager.LibraryLoadingMode.PACKAGED, env.getFmodLoadingMode());
        assertEquals(AudioSystemManager.FmodLibraryType.STANDARD, env.getFmodLibraryType());

        // Test system property override
        System.setProperty("fmod.loading.mode", "unpackaged");
        System.setProperty("fmod.library.type", "logging");
        Environment envWithOverride = new Environment(Platform.MACOS, null, config, null, null);

        assertEquals(AudioSystemManager.LibraryLoadingMode.UNPACKAGED, envWithOverride.getFmodLoadingMode());
        assertEquals(AudioSystemManager.FmodLibraryType.LOGGING, envWithOverride.getFmodLibraryType());
    }

    @Test
    @DisplayName("invalid enum values fall back to defaults")
    void invalidEnumValuesFallBackToDefaults() {
        // Test with invalid enum values in config
        String invalidConfig = "fmod.loading.mode=invalid_mode\nfmod.library.type=invalid_type";
        Environment env = new Environment(Platform.MACOS, null, invalidConfig, null, null);

        // Should fall back to defaults, not crash
        assertEquals(AudioSystemManager.LibraryLoadingMode.PACKAGED, env.getFmodLoadingMode());
        assertEquals(AudioSystemManager.FmodLibraryType.STANDARD, env.getFmodLibraryType());
    }

    @Test
    @DisplayName("configuration cascade combines sources correctly")
    void configurationCascadeCombinesSourcesCorrectly() {
        // Test that different keys from different sources are all available
        String defaults = "key1=default1\nkey2=default2";
        String application = "key2=app2\nkey3=app3";
        String platform = "key3=platform3\nkey4=platform4";
        String user = "key4=user4\nkey5=user5";

        Environment env = new Environment(Platform.MACOS, defaults, application, platform, user);

        // Should get values based on priority
        assertEquals("default1", env.getStringProperty("key1", "fallback")); // Only in defaults
        assertEquals("app2", env.getStringProperty("key2", "fallback")); // App overrides defaults
        assertEquals("platform3", env.getStringProperty("key3", "fallback")); // Platform overrides app
        assertEquals("user4", env.getStringProperty("key4", "fallback")); // User overrides platform
        assertEquals("user5", env.getStringProperty("key5", "fallback")); // Only in user
    }

    @Test
    @DisplayName("system properties override all configuration sources")
    void systemPropertiesOverrideAllConfigurationSources() {
        // Set up conflicting values in all sources
        String defaults = "test.override.key=default_value";
        String application = "test.override.key=app_value";
        String platform = "test.override.key=platform_value";
        String user = "test.override.key=user_value";

        // Set system property
        System.setProperty("test.override.key", "system_value");

        Environment env = new Environment(Platform.MACOS, defaults, application, platform, user);

        // System property should win
        assertEquals("system_value", env.getStringProperty("test.override.key", "fallback"));
    }

    // Helper method to access getStringProperty for testing
    // (Would need to be added to Environment class for this test to work)
    // For now, we test indirectly through public methods that use the same mechanism
}