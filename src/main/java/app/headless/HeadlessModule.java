package app.headless;

import com.google.inject.AbstractModule;
import core.CoreModule;
import core.dispatch.EventDispatcher;

/**
 * Guice module for headless application dependency injection configuration.
 *
 * <p>Configures only the core bindings needed for headless operation, without any UI components.
 */
public class HeadlessModule extends AbstractModule {

    @Override
    protected void configure() {
        // Bind headless event dispatcher
        bind(EventDispatcher.class).to(HeadlessEventDispatcher.class).asEagerSingleton();

        // Install shared core module
        install(new CoreModule());

        // Note: In headless mode, we don't install:
        // - waveform.Module (has UI dependencies)
        // - state.Module (has UI dependencies)
        // - ui.Module (Swing UI)
        // - actions.Module (Swing actions)
    }
}
