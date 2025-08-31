package a2;

import java.lang.reflect.InvocationTargetException;
import lombok.NonNull;

/** Factory for creating audio engine instances. */
public final class AudioEngineFactory {

    private AudioEngineFactory() {
        // Prevent instantiation
    }

    /**
     * Creates audio engine with given configuration.
     *
     * @param config The configuration for the engine (including engine type and mode)
     * @return The created audio engine
     * @throws UnsupportedAudioEngineException if engine type is not supported
     * @throws InvalidAudioConfigException if configuration contains invalid values
     */
    public static AudioEngine create(@NonNull AudioEngineConfig config)
            throws UnsupportedAudioEngineException, InvalidAudioConfigException {

        String className =
                switch (config.getEngineType()) {
                    case "fmod" -> "a2.fmod.FmodAudioEngine";
                    default -> throw new UnsupportedAudioEngineException(config.getEngineType());
                };

        try {
            Class<?> engineClass = Class.forName(className);
            AudioEngine engine = (AudioEngine) engineClass.getDeclaredConstructor().newInstance();

            // Call package-private init method using reflection
            var initMethod = engineClass.getDeclaredMethod("init", AudioEngineConfig.class);
            initMethod.invoke(engine, config);
            return engine;
        } catch (InvocationTargetException e) {
            // Unwrap the actual exception thrown by init
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                // Configuration validation failed
                throw new InvalidAudioConfigException(cause.getMessage(), cause);
            }
            throw new UnsupportedAudioEngineException(config.getEngineType(), e);
        } catch (Exception e) {
            throw new UnsupportedAudioEngineException(config.getEngineType(), e);
        }
    }
}
