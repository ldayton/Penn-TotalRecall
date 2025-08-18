package integration;

import static org.junit.jupiter.api.Assertions.*;

import audio.AudioSystemLoader;
import audio.FmodCore;
import components.WindowManager;
import di.GuiceBootstrap;
import env.AppConfig;
import env.KeyboardManager;
import env.LookAndFeelManager;
import env.UpdateManager;
import env.UserManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Basic smoke tests for Guice dependency injection configuration.
 *
 * <p>These tests verify that the Guice injector can be created and that basic dependency resolution
 * works. This catches simple configuration issues like missing @Inject annotations but does not
 * test actual application functionality or real integration scenarios.
 */
@DisplayName("Guice Configuration Smoke Tests")
class GuiceBootstrapIntegrationTest {
    private static final Logger logger =
            LoggerFactory.getLogger(GuiceBootstrapIntegrationTest.class);

    @Test
    @DisplayName("guice injector can be created without errors")
    void guiceInjectorCanBeCreatedWithoutErrors() {
        logger.info("Testing basic Guice injector creation...");

        // This only tests that Guice can create objects, not that the app actually works
        GuiceBootstrap bootstrap = null;
        try {
            bootstrap = GuiceBootstrap.create();
            assertNotNull(bootstrap, "Bootstrap should be created successfully");
            logger.info("✅ GuiceBootstrap.create() succeeded");
        } catch (Exception e) {
            fail("Failed to create Guice bootstrap: " + e.getMessage(), e);
        }

        // Verify that basic dependencies can be instantiated (doesn't test functionality)
        assertNotNull(
                GuiceBootstrap.getInjectedInstance(AppConfig.class),
                "AppConfig should be instantiable via DI");
        logger.info("✅ AppConfig can be created");

        assertNotNull(
                GuiceBootstrap.getInjectedInstance(UserManager.class),
                "UserManager should be instantiable via DI");
        logger.info("✅ UserManager can be created");

        assertNotNull(
                GuiceBootstrap.getInjectedInstance(AudioSystemLoader.class),
                "AudioSystemLoader should be instantiable via DI");
        logger.info("✅ AudioSystemLoader can be created");

        assertNotNull(
                GuiceBootstrap.getInjectedInstance(FmodCore.class),
                "FmodCore should be instantiable via DI");
        logger.info("✅ FmodCore can be created");

        assertNotNull(
                GuiceBootstrap.getInjectedInstance(WindowManager.class),
                "WindowManager should be instantiable via DI");
        logger.info("✅ WindowManager can be created");

        assertNotNull(
                GuiceBootstrap.getInjectedInstance(UpdateManager.class),
                "UpdateManager should be instantiable via DI");
        logger.info("✅ UpdateManager can be created");

        assertNotNull(
                GuiceBootstrap.getInjectedInstance(LookAndFeelManager.class),
                "LookAndFeelManager should be instantiable via DI");
        logger.info("✅ LookAndFeelManager can be created");

        assertNotNull(
                GuiceBootstrap.getInjectedInstance(KeyboardManager.class),
                "KeyboardManager should be instantiable via DI");
        logger.info("✅ KeyboardManager can be created");

        logger.info("✅ Basic Guice configuration smoke test passed");
    }

    @Test
    @DisplayName("fmod objects can be created without constructor errors")
    void fmodObjectsCanBeCreatedWithoutConstructorErrors() {
        logger.info("Testing basic FMOD object creation via DI...");

        // Create bootstrap to initialize global injector
        GuiceBootstrap.create();

        // Get FmodCore through DI (doesn't test that audio actually works)
        FmodCore fmodCore = GuiceBootstrap.getInjectedInstance(FmodCore.class);
        assertNotNull(fmodCore, "FmodCore should be creatable via DI");

        // Verify we can call a basic method without crashing
        // (This doesn't test audio functionality, just that the object was constructed properly)
        assertFalse(
                fmodCore.playbackInProgress(),
                "Initial state should be not playing (basic method call works)");

        logger.info("✅ FMOD objects can be created via DI");
    }
}
