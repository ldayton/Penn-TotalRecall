package app.di;

import audio.fmod.FmodModule;
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

        // Install audio module for audio engine bindings
        install(new audio.Module());

        // Install waveform module for waveform bindings
        install(new waveform.Module());

        // Install state module for session management bindings
        install(new state.Module());

        // Install env module for environment and platform bindings
        install(new env.Module());

        // Install app module for application-level bindings
        install(new app.Module());

        // Install ui module for user interface bindings
        install(new ui.Module());

        // Install actions module for action management bindings
        install(new actions.Module());
    }
}
