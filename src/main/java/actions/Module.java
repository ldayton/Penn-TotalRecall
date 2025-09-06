package actions;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

/**
 * Guice module for the actions package.
 *
 * <p>Configures bindings for action management and parsing components.
 */
public class Module extends AbstractModule {

    @Override
    protected void configure() {
        // Action management
        bind(ActionsManager.class).in(Singleton.class);

        // Action file parsing
        bind(ActionsFileParser.class);
    }
}
