package app.headless;

import com.google.inject.Guice;
import com.google.inject.Injector;
import core.actions.ActionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Headless application entry point.
 *
 * <p>Provides a minimal application bootstrap for command-line and server-side operations without
 * any UI dependencies.
 */
public class HeadlessApp {
    private static final Logger logger = LoggerFactory.getLogger(HeadlessApp.class);
    private static Injector globalInjector;

    /** Creates the Guice injector and returns a headless application instance. */
    public static HeadlessApp create() {
        globalInjector = Guice.createInjector(new HeadlessModule());

        // Initialize ActionRegistry which auto-registers all actions
        globalInjector.getInstance(ActionRegistry.class);

        return globalInjector.getInstance(HeadlessApp.class);
    }

    /** Starts the headless application. */
    public void startApplication() {
        logger.info("Headless application started");
        // Additional headless initialization would go here
    }

    /**
     * Gets an instance from the global injector.
     *
     * @param clazz the class to get
     * @return the instance from the injector
     * @throws IllegalStateException if the injector hasn't been created yet
     */
    public static <T> T getInjectedInstance(Class<T> clazz) {
        if (globalInjector == null) {
            throw new IllegalStateException("HeadlessApp not initialized. Call create() first.");
        }
        return globalInjector.getInstance(clazz);
    }
}
