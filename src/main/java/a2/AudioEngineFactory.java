package a2;

import lombok.NonNull;

/** Factory for creating audio engine instances. */
public final class AudioEngineFactory {

    private AudioEngineFactory() {
        // Prevent instantiation
    }

    /**
     * Creates audio engine with given configuration and engine type.
     *
     * @param config The configuration for the engine
     * @param engineType The type of engine ("fmod" or others in future)
     * @param mode The mode for optimization (PLAYBACK or RENDERING)
     * @return The created audio engine
     * @throws UnsupportedAudioEngineException if engine type is not supported
     */
    public static AudioEngine create(
            @NonNull AudioEngineConfig config,
            @NonNull String engineType,
            @NonNull AudioEngineConfig.Mode mode)
            throws UnsupportedAudioEngineException {

        String className =
                switch (engineType) {
                    case "fmod" -> "a2.fmod.FmodAudioEngine";
                    default -> throw new UnsupportedAudioEngineException(engineType);
                };

        try {
            Class<?> engineClass = Class.forName(className);
            AudioEngine engine = (AudioEngine) engineClass.getDeclaredConstructor().newInstance();

            // Call package-private init method using reflection
            var initMethod =
                    engineClass.getDeclaredMethod(
                            "init", AudioEngineConfig.class, audio.AudioSystemLoader.class);
            initMethod.invoke(config);
            return engine;
        } catch (Exception e) {
            throw new UnsupportedAudioEngineException(engineType, e);
        }
    }
}
