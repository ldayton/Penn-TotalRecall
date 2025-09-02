package a2.fmod;

import a2.AudioEngine;
import a2.AudioEngineConfig;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Guice provider for FmodAudioEngine instances. Creates and initializes the engine lazily on first
 * access.
 */
@Singleton
@Slf4j
public class FmodAudioEngineProvider implements Provider<AudioEngine> {

    private final AudioEngineConfig config;
    private volatile AudioEngine instance;

    @Inject
    public FmodAudioEngineProvider(@NonNull AudioEngineConfig config) {
        this.config = config;
    }

    @Override
    public AudioEngine get() {
        // Double-checked locking for lazy initialization
        if (instance == null) {
            synchronized (this) {
                if (instance == null) {
                    log.debug("Creating new FmodAudioEngine instance with config: {}", config);
                    FmodAudioEngine engine = new FmodAudioEngine();
                    engine.init(config);
                    instance = engine;
                }
            }
        }
        return instance;
    }

    /** Get the current instance if it exists, null otherwise. Useful for cleanup or testing. */
    public AudioEngine getCurrentInstance() {
        return instance;
    }

    /**
     * Clear the current instance, forcing a new one to be created on next get(). Useful for testing
     * or reconfiguration.
     */
    public synchronized void clearInstance() {
        if (instance != null) {
            try {
                instance.close();
            } catch (Exception e) {
                log.warn("Error closing audio engine during instance clear", e);
            }
            instance = null;
        }
    }
}
