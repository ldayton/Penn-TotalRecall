package app.swing;

import com.google.inject.AbstractModule;
import core.CoreModule;
import core.dispatch.EventDispatcher;
import ui.SwingEventDispatcher;

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

        // Install waveform module for waveform bindings
        install(new waveform.Module());

        // Install state module for session management bindings
        install(new state.Module());

        // Install ui module for user interface bindings
        install(new ui.Module());

        // Install actions module for action management bindings
        install(new actions.Module());
    }
}
