package ui.actions;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

/**
 * Guice module for the actions package.
 *
 * <p>Configures bindings for action management and parsing components. Action implementations are
 * now auto-discovered by core.actions.Module.
 */
public class Module extends AbstractModule {

    @Override
    protected void configure() {
        // Action management
        bind(ActionsManager.class).in(Singleton.class);

        // Action file parsing
        bind(ActionsFileParser.class);

        // Note: Action auto-discovery and ActionRegistry binding
        // are now handled by core.actions.Module
    }
}
