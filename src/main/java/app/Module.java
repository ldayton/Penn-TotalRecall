package app;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

/**
 * Guice module for the app package.
 *
 * <p>Configures bindings for application-level components.
 */
public class Module extends AbstractModule {

    @Override
    protected void configure() {
        // Development and debugging
        bind(DevModeFileAutoLoader.class).in(Singleton.class);
    }
}
