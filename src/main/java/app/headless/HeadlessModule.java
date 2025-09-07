package app.headless;

import com.google.inject.AbstractModule;

/**
 * Guice module for headless application dependency injection configuration.
 *
 * <p>Configures only the core bindings needed for headless operation, without any UI components.
 * Currently only supports core actions.
 */
public class HeadlessModule extends AbstractModule {

    @Override
    protected void configure() {
        // Install actions module for action management bindings
        install(new actions.Module());

        // Note: Additional modules would be added here as more functionality
        // becomes available in headless mode
    }
}
