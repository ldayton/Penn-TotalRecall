package app.di;

import a2.fmod.FmodModule;
import com.google.inject.AbstractModule;

/**
 * Guice module for dependency injection configuration.
 *
 * <p>Binds modern, constructor-injected classes for update checking and window management. FMOD
 * classes remain static for now to avoid complex migration.
 */
public class AppModule extends AbstractModule {

    @Override
    protected void configure() {
        // Install FMOD module for audio system dependencies
        install(new FmodModule());

        // Install a2 module for audio engine bindings
        install(new a2.Module());

        // Install w2 module for waveform bindings
        install(new w2.Module());

        // Install s2 module for session management bindings
        install(new s2.Module());

        // Install env module for environment and platform bindings
        install(new env.Module());

        // Install ui module for user interface bindings
        install(new ui.Module());

        // Install actions module for action management bindings
        install(new actions.Module());
    }
}
