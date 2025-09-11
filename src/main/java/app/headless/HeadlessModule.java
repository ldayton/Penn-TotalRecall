package app.headless;

import com.google.inject.AbstractModule;
import core.CoreModule;
import core.dispatch.EventDispatcher;
import core.services.FileSelectionService;
import core.state.WaveformSessionDataSource;

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

        // Bind headless service stubs
        bind(FileSelectionService.class)
                .to(app.headless.adapters.HeadlessFileSelectionService.class)
                .asEagerSingleton();
        bind(WaveformSessionDataSource.class)
                .to(app.headless.adapters.HeadlessWaveformSessionDataSource.class)
                .asEagerSingleton();

        // Install shared core module
        install(new CoreModule());

        // Install core actions module (no UI dependencies)
        install(new core.actions.Module());

        // Note: In headless mode, we don't install:
        // - waveform.Module (has UI dependencies)
        // - state.Module (has UI dependencies)
        // - ui.Module (Swing UI)
    }
}
