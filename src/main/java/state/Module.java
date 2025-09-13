package state;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import core.state.AudioSessionManager;
import core.state.AudioSessionStateMachine;
import core.state.WaveformSessionDataSource;
import core.state.WaveformViewport;

/**
 * Guice module for the s2 (session) package.
 *
 * <p>Configures bindings for audio session management and waveform data source components.
 */
public class Module extends AbstractModule {

    @Override
    protected void configure() {
        // Core session management
        bind(AudioSessionManager.class).in(Singleton.class);
        bind(AudioSessionStateMachine.class).in(Singleton.class);
        bind(AnnotationManager.class).in(Singleton.class);

        // Waveform data sources and management
        bind(WaveformManager.class).in(Singleton.class);
        bind(WaveformViewport.class).in(Singleton.class);
        bind(WaveformPaintDataSource.class).in(Singleton.class);

        // Bind the interface to the implementation for session data
        bind(WaveformSessionDataSource.class).to(AudioSessionManager.class).in(Singleton.class);

        // Wordpool management (listens for WordpoolFileSelectedEvent)
        bind(WordpoolManager.class).asEagerSingleton();
    }
}
