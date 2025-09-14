package core.audio;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import core.audio.fmod.FmodAudioEngine;
import core.audio.fmod.FmodSampleReader;
import core.audio.session.AudioSessionDataSource;
import core.audio.session.AudioSessionManager;
import core.audio.session.AudioSessionStateMachine;

/**
 * Guice module for the a2 (audio engine) package.
 *
 * <p>Configures bindings for audio-related interfaces to their FMOD implementations.
 */
public class Module extends AbstractModule {

    @Override
    protected void configure() {
        // Bind audio engine interface to FMOD implementation
        bind(AudioEngine.class).to(FmodAudioEngine.class).in(Singleton.class);

        // Bind sample reader interface to FMOD implementation
        // Note: Not singleton because each Waveform needs its own reader instance
        bind(SampleReader.class).to(FmodSampleReader.class);

        // Core session management
        bind(AudioSessionManager.class).in(Singleton.class);
        bind(AudioSessionStateMachine.class).in(Singleton.class);

        // Bind the interface to the implementation for session data
        bind(AudioSessionDataSource.class).to(AudioSessionManager.class).in(Singleton.class);
    }
}
