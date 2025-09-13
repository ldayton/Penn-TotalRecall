package core.waveform;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

/** Guice module for waveform components. */
public class Module extends AbstractModule {

    @Override
    protected void configure() {
        // Waveform management
        bind(WaveformManager.class).in(Singleton.class);
    }
}
