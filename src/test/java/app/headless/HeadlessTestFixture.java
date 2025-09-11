package app.headless;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 * Base fixture for headless integration tests that need the DI container. Handles bootstrap and
 * cleanup of HeadlessApp without any UI dependencies.
 */
@Slf4j
public abstract class HeadlessTestFixture {
    private static final int STARTUP_TIMEOUT_SECONDS = 5;

    protected HeadlessApp app;

    @BeforeEach
    void setUp() throws Exception {
        log.info("Starting HeadlessApp for test: {}", getClass().getSimpleName());

        // Set headless property
        System.setProperty("java.awt.headless", "true");

        // Bootstrap the application asynchronously
        CompletableFuture<HeadlessApp> future =
                CompletableFuture.supplyAsync(
                        () -> {
                            HeadlessApp bootstrap = HeadlessApp.create();
                            bootstrap.startApplication();
                            return bootstrap;
                        });

        // Wait for bootstrap to complete
        app = future.get(STARTUP_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        log.info("HeadlessApp ready for test");
    }

    @AfterEach
    void tearDown() throws Exception {
        log.info("Cleaning up HeadlessApp after test");

        // Reset headless property
        System.clearProperty("java.awt.headless");

        // Give cleanup a moment to complete
        Thread.sleep(100);
        log.info("HeadlessApp cleanup complete");
    }

    /** Gets an instance from the DI container. */
    protected <T> T getInstance(Class<T> clazz) {
        return HeadlessApp.getInjectedInstance(clazz);
    }

    /**
     * Gets an instance from the DI container, with a fallback if not available. Useful for optional
     * dependencies.
     */
    protected <T> T getInstance(Class<T> clazz, T defaultValue) {
        try {
            return HeadlessApp.getInjectedInstance(clazz);
        } catch (Exception e) {
            log.debug("Instance not available for {}, using default", clazz.getSimpleName());
            return defaultValue;
        }
    }
}
