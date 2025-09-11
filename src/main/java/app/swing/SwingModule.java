package app.swing;

import com.google.inject.AbstractModule;
import core.CoreModule;
import core.dispatch.EventDispatcher;
import ui.adapters.SwingEventDispatcher;

/**
 * Guice module for Swing UI application dependency injection configuration.
 *
 * <p>Configures all bindings needed for the Swing-based desktop application, including UI
 * components, audio system, and core services.
 */
public class SwingModule extends AbstractModule {

    @Override
    protected void configure() {
        // Bind Swing event dispatcher
        bind(EventDispatcher.class).to(SwingEventDispatcher.class).asEagerSingleton();

        // Install shared core module
        install(new CoreModule());

        // Install state module for session management bindings
        install(new state.Module());

        // Install ui module for user interface bindings
        // Note: ui.Module now installs ui.actions.Module automatically
        install(new ui.Module());
    }
}
