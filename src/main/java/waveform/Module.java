package waveform;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

/**
 * Guice module for the w2 (waveform) package.
 *
 * <p>Configures bindings for waveform-related components.
 */
public class Module extends AbstractModule {

    @Override
    protected void configure() {
        // Bind waveform painter - singleton as it manages the repaint timer
        bind(WaveformPainter.class).in(Singleton.class);

        // Note: Waveform itself is not bound here as it's created per audio file
        // by WaveformManager with specific parameters
    }
}
