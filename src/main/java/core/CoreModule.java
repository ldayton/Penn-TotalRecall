package core;

import com.google.inject.AbstractModule;
import core.audio.fmod.FmodModule;

/**
 * Shared core module that installs all non-UI dependencies.
 *
 * <p>This module is used by both Swing and headless applications, providing all core functionality
 * including audio and environment configuration.
 *
 * <p>Note: EventDispatcher is NOT bound here since Swing and Headless need different
 * implementations.
 */
public class CoreModule extends AbstractModule {

    @Override
    protected void configure() {
        // Install FMOD module for audio system dependencies
        install(new FmodModule());

        // Install audio module for audio engine bindings
        install(new core.audio.Module());

        // Install env module for environment and platform bindings
        // This also installs core.actions.Module
        install(new core.env.Module());

        // Install preferences module
        install(new core.preferences.Module());
    }
}
