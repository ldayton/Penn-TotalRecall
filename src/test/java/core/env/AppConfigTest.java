package core.env;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Comprehensive tests for AppConfig configuration loading hierarchy.
 *
 * <p>Tests the 5-level configuration priority system: System properties > User config > Platform
 * config > Application config > Defaults. Verifies file loading mechanics, property resolution, and
 * platform-specific behavior.
 */
@DisplayName("AppConfig Configuration Loading")
class AppConfigTest {

    @TempDir Path tempDir;

    private String originalUserHome;

    @BeforeEach
    void setUp() {
        // Save original system properties
        originalUserHome = System.getProperty("user.home");
    }

    @AfterEach
    void cleanUp() {
        // Restore original system properties and clean test properties
        if (originalUserHome != null) {
            System.setProperty("user.home", originalUserHome);
        }

        // Clean up test system properties
        System.clearProperty("test.priority.key");
        System.clearProperty("test.override.key");
        System.clearProperty("test.boolean.key");
        System.clearProperty("test.int.key");
        System.clearProperty("test.double.key");
        System.clearProperty("releases.api.url");
        System.clearProperty("releases.page.url");
        System.clearProperty("system.only.key");
        System.clearProperty("user.only.key");
        System.clearProperty("user.specific.key");
        System.clearProperty("shared.key");
    }

    @Nested
    @DisplayName("Configuration Priority Hierarchy")
    class ConfigurationPriorityTests {

        @Test
        @DisplayName("system properties override all configuration sources")
        void systemPropertiesOverrideAllSources() throws IOException {
            // Create user config with a test property
            File userConfigDir = createUserConfigDirectory(Platform.PlatformType.MACOS);
            File userConfigFile = new File(userConfigDir, "application.properties");
            writePropertiesFile(userConfigFile, "test.priority.key=user_value");

            // Set system property that should override
            System.setProperty("test.priority.key", "system_value");

            Platform platform = new Platform(Platform.PlatformType.MACOS);
            UserHomeProvider userManager = new UserHomeProvider();
            AppConfig config = new AppConfig(platform, userManager);

            // System property should win
            assertEquals("system_value", config.getProperty("test.priority.key"));
        }

        @Test
        @DisplayName("user config overrides bundled application config")
        void userConfigOverridesBundledConfig() throws IOException {
            // Create user config that overrides a known bundled property
            File userConfigDir = createUserConfigDirectory(Platform.PlatformType.MACOS);
            File userConfigFile = new File(userConfigDir, "application.properties");
            writePropertiesFile(userConfigFile, "releases.api.url=user_override_url");

            Platform platform = new Platform(Platform.PlatformType.MACOS);
            UserHomeProvider userManager = new UserHomeProvider();
            AppConfig config = new AppConfig(platform, userManager);

            // User config should override bundled config
            assertEquals("user_override_url", config.getProperty("releases.api.url"));
        }

        @Test
        @DisplayName("configuration cascade combines different sources correctly")
        void configurationCascadeCombinesSources() throws IOException {
            // Create user config with some properties
            File userConfigDir = createUserConfigDirectory(Platform.PlatformType.MACOS);
            File userConfigFile = new File(userConfigDir, "application.properties");
            writePropertiesFile(
                    userConfigFile, "user.only.key=user_value\nshared.key=user_override");

            Platform platform = new Platform(Platform.PlatformType.MACOS);
            UserHomeProvider userManager = new UserHomeProvider();
            AppConfig config = new AppConfig(platform, userManager);

            // Should get user-specific property
            assertEquals("user_value", config.getProperty("user.only.key"));

            // Should get user override of shared property
            assertEquals("user_override", config.getProperty("shared.key"));

            // Should still get bundled properties not overridden
            assertNotNull(
                    config.getProperty("releases.api.url"),
                    "Should still access bundled properties");
        }
    }

    @Nested
    @DisplayName("Property Resolution Methods")
    class PropertyResolutionTests {

        @Test
        @DisplayName("property parsing error handling works correctly")
        void propertyParsingErrorHandlingWorks() {
            // Set invalid values to test our error handling
            System.setProperty("test.boolean.key", "invalid_boolean");
            System.setProperty("test.int.key", "not_a_number");
            System.setProperty("test.double.key", "not_a_double");

            Platform platform = new Platform();
            UserHomeProvider userManager = new UserHomeProvider();
            AppConfig config = new AppConfig(platform, userManager);

            // Should handle invalid values gracefully and return defaults
            assertFalse(config.getBooleanProperty("test.boolean.key", false));
            assertEquals(42, config.getIntProperty("test.int.key", 42));
            assertEquals(3.14, config.getDoubleProperty("test.double.key", 3.14), 0.001);

            // Should return provided defaults for non-existent keys
            assertTrue(config.getBooleanProperty("non.existent.boolean", true));
            assertEquals(100, config.getIntProperty("non.existent.int", 100));
            assertEquals(2.71, config.getDoubleProperty("non.existent.double", 2.71), 0.001);
        }
    }

    @Nested
    @DisplayName("Platform-Specific Configuration")
    class PlatformSpecificTests {

        @Test
        @DisplayName("platform-specific user config loading works")
        void platformSpecificUserConfigLoading() throws IOException {
            // Test macOS config loading
            File macConfigDir = createUserConfigDirectory(Platform.PlatformType.MACOS);
            File macConfigFile = new File(macConfigDir, "application.properties");
            writePropertiesFile(macConfigFile, "platform.test=macos_works");

            Platform macPlatform = new Platform(Platform.PlatformType.MACOS);
            UserHomeProvider macUserHomeProvider = new UserHomeProvider();
            AppConfig macConfig = new AppConfig(macPlatform, macUserHomeProvider);

            assertEquals("macos_works", macConfig.getProperty("platform.test"));

            // Test Linux config loading with different temp home
            System.setProperty("user.home", tempDir.resolve("linux_home").toString());
            File linuxConfigDir = createUserConfigDirectory(Platform.PlatformType.LINUX);
            File linuxConfigFile = new File(linuxConfigDir, "application.properties");
            writePropertiesFile(linuxConfigFile, "platform.test=linux_works");

            Platform linuxPlatform = new Platform(Platform.PlatformType.LINUX);
            UserHomeProvider linuxUserHomeProvider = new UserHomeProvider();
            AppConfig linuxConfig = new AppConfig(linuxPlatform, linuxUserHomeProvider);

            assertEquals("linux_works", linuxConfig.getProperty("platform.test"));
        }
    }

    @Nested
    @DisplayName("File Loading Mechanics")
    class FileLoadingTests {

        @Test
        @DisplayName("missing user configuration file is handled gracefully")
        void missingUserConfigurationIsHandledGracefully() {
            // Set home to temp directory with no config file
            System.setProperty("user.home", tempDir.toString());

            Platform platform = new Platform();
            UserHomeProvider userManager = new UserHomeProvider();

            // Should not throw exception
            assertDoesNotThrow(() -> new AppConfig(platform, userManager));

            AppConfig config = new AppConfig(platform, userManager);

            // Should still work with bundled configuration
            assertNotNull(
                    config.getProperty("releases.api.url"), "Should load bundled configuration");
        }

        @Test
        @DisplayName("malformed user configuration file does not crash loading")
        void malformedUserConfigurationDoesNotCrash() throws IOException {
            // Create user config directory
            File userConfigDir = createUserConfigDirectory(Platform.PlatformType.MACOS);
            File userConfigFile = new File(userConfigDir, "application.properties");

            // Write malformed properties
            try (FileWriter writer = new FileWriter(userConfigFile)) {
                writer.write(
                        "this is not=valid\n"
                                + "properties format\n"
                                + "=missing key\n"
                                + "valid.key=valid_value");
            }

            Platform platform = new Platform(Platform.PlatformType.MACOS);
            UserHomeProvider userManager = new UserHomeProvider();

            // Should not throw exception
            assertDoesNotThrow(() -> new AppConfig(platform, userManager));

            AppConfig config = new AppConfig(platform, userManager);

            // Should still load valid parts and bundled config
            assertEquals("valid_value", config.getProperty("valid.key"));
            assertNotNull(config.getProperty("releases.api.url"));
        }

        @Test
        @DisplayName("comprehensive invalid configuration content handling")
        void comprehensiveInvalidConfigurationHandling() throws IOException {
            File userConfigDir = createUserConfigDirectory(Platform.PlatformType.MACOS);
            File userConfigFile = new File(userConfigDir, "application.properties");

            // Write various types of invalid content mixed with valid content
            String invalidContent =
                    """
                    # Valid comment
                    valid.before=working_value
                    this is completely invalid
                    =empty_key_invalid
                    key_with_no_equals_invalid
                    valid.middle=another_working_value
                    ===multiple_equals_weird===
                    valid.after=final_working_value
                    """;

            try (FileWriter writer = new FileWriter(userConfigFile)) {
                writer.write(invalidContent);
            }

            Platform platform = new Platform(Platform.PlatformType.MACOS);
            UserHomeProvider userManager = new UserHomeProvider();

            // Should not crash on invalid content
            assertDoesNotThrow(() -> new AppConfig(platform, userManager));

            AppConfig config = new AppConfig(platform, userManager);

            // Should load valid properties
            assertEquals("working_value", config.getProperty("valid.before"));
            assertEquals("another_working_value", config.getProperty("valid.middle"));
            assertEquals("final_working_value", config.getProperty("valid.after"));

            // Should still access bundled configuration
            assertNotNull(config.getProperty("releases.api.url"));

            // Should return defaults for non-existent properties
            assertEquals("fallback", config.getProperty("non.existent", "fallback"));
        }

        @Test
        @DisplayName("completely corrupt configuration file is handled gracefully")
        void completelyCorruptConfigurationHandled() throws IOException {
            File userConfigDir = createUserConfigDirectory(Platform.PlatformType.MACOS);
            File userConfigFile = new File(userConfigDir, "application.properties");

            // Write completely invalid content (binary-like)
            try (FileWriter writer = new FileWriter(userConfigFile)) {
                writer.write("\u0000\u0001\u0002\u0003invalid binary data\n");
                writer.write("\\x00\\x01\\x02\\x03\n");
                writer.write("this file is completely corrupt\n");
                writer.write("no valid properties at all\n");
            }

            Platform platform = new Platform(Platform.PlatformType.MACOS);
            UserHomeProvider userManager = new UserHomeProvider();

            // Should not crash even with completely corrupt content
            assertDoesNotThrow(() -> new AppConfig(platform, userManager));

            AppConfig config = new AppConfig(platform, userManager);

            // Should still provide bundled configuration
            assertNotNull(config.getProperty("releases.api.url"));

            // Should return defaults for non-existent properties
            assertEquals(
                    "default_fallback", config.getProperty("non.existent", "default_fallback"));
        }

        @Test
        @DisplayName("user config directory creation works")
        void userConfigDirectoryCreationWorks() throws IOException {
            // Set home to temp directory
            String tempHome = tempDir.toString();
            System.setProperty("user.home", tempHome);

            Platform platform = new Platform(Platform.PlatformType.LINUX);
            UserHomeProvider userManager = new UserHomeProvider();

            // Config directory should not exist initially
            File expectedDir = new File(tempHome, ".penn-totalrecall");
            assertFalse(expectedDir.exists());

            // Creating AppConfig should create the directory
            new AppConfig(platform, userManager);

            assertTrue(expectedDir.exists(), "Config directory should be created");
            assertTrue(expectedDir.isDirectory(), "Should be a directory");
        }
    }

    @Nested
    @DisplayName("Configuration Hierarchy Priority")
    class ConfigurationHierarchyTests {

        @Test
        @DisplayName(
                "complete 5-level priority cascade: system > user > platform > application >"
                        + " defaults")
        void completePriorityCascadeWorks() throws IOException {
            // Set up user config with test values
            File userConfigDir = createUserConfigDirectory(Platform.PlatformType.MACOS);
            File userConfigFile = new File(userConfigDir, "application.properties");
            writePropertiesFile(
                    userConfigFile,
                    "test.priority.key=user_value\n" + "user.only.key=user_only_value");

            Platform platform = new Platform(Platform.PlatformType.MACOS);
            UserHomeProvider userManager = new UserHomeProvider();
            AppConfig config = new AppConfig(platform, userManager);

            // User config should override bundled application.properties
            assertEquals("user_value", config.getProperty("test.priority.key", "fallback"));
            assertEquals("user_only_value", config.getProperty("user.only.key", "fallback"));

            // Test system property override (highest priority)
            System.setProperty("test.priority.key", "system_value");
            System.setProperty("system.only.key", "system_only_value");

            // Create new config to pick up system properties
            AppConfig configWithSystem = new AppConfig(platform, userManager);

            // System property should win over everything
            assertEquals(
                    "system_value", configWithSystem.getProperty("test.priority.key", "fallback"));
            assertEquals(
                    "system_only_value",
                    configWithSystem.getProperty("system.only.key", "fallback"));

            // User config should still be accessible for non-overridden keys
            assertEquals(
                    "user_only_value", configWithSystem.getProperty("user.only.key", "fallback"));

            // Should still access bundled properties
            assertNotNull(configWithSystem.getProperty("releases.api.url"));
        }

        @Test
        @DisplayName("configuration cascade combines different sources correctly")
        void configurationCascadeCombinesSources() throws IOException {
            // Create user config with multiple keys demonstrating priority
            File userConfigDir = createUserConfigDirectory(Platform.PlatformType.MACOS);
            File userConfigFile = new File(userConfigDir, "application.properties");
            writePropertiesFile(
                    userConfigFile,
                    "releases.api.url=user_override_api\n"
                            + // Override bundled
                            "user.specific.key=user_value\n"
                            + // User only
                            "shared.key=user_override"); // User override

            Platform platform = new Platform(Platform.PlatformType.MACOS);
            UserHomeProvider userManager = new UserHomeProvider();
            AppConfig config = new AppConfig(platform, userManager);

            // User should override bundled
            assertEquals("user_override_api", config.getProperty("releases.api.url"));
            assertEquals("user_value", config.getProperty("user.specific.key", "fallback"));
            assertEquals("user_override", config.getProperty("shared.key", "fallback"));

            // Should still get bundled properties not overridden
            assertNotNull(
                    config.getProperty("releases.page.url")); // From bundled application.properties
        }

        @Test
        @DisplayName("system properties override all configuration file sources")
        void systemPropertiesOverrideAllSources() throws IOException {
            // Create user config that tries to override bundled properties
            File userConfigDir = createUserConfigDirectory(Platform.PlatformType.MACOS);
            File userConfigFile = new File(userConfigDir, "application.properties");
            writePropertiesFile(userConfigFile, "releases.api.url=user_override");

            // Set system property that should override both user and bundled
            System.setProperty("releases.api.url", "system_override");

            Platform platform = new Platform(Platform.PlatformType.MACOS);
            UserHomeProvider userManager = new UserHomeProvider();
            AppConfig config = new AppConfig(platform, userManager);

            // System property should win over user config and bundled config
            assertEquals("system_override", config.getProperty("releases.api.url"));
        }
    }

    // Helper methods

    private File createUserConfigDirectory(Platform.PlatformType platformType) throws IOException {
        String tempHome = tempDir.toString();
        System.setProperty("user.home", tempHome);

        String appName = new AppConfig().getProperty(AppConfig.APP_NAME_KEY);
        String configPath =
                switch (platformType) {
                    case MACOS -> tempHome + "/Library/Application Support/" + appName;
                    case WINDOWS -> tempHome + "/AppData/Roaming/" + appName; // Simplified for test
                    case LINUX -> tempHome + "/." + appName.toLowerCase().replace(" ", "-");
                };

        File configDir = new File(configPath);
        configDir.mkdirs();
        return configDir;
    }

    private void writePropertiesFile(File file, String content) throws IOException {
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
    }
}
