package audio;

import audio.fmod.FmodAudioEngine;
import audio.fmod.FmodSampleReader;
import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

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
    }
}
