package audio.fmod;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.sun.jna.Pointer;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Guice module for FMOD audio system dependency injection. Provides bindings for all FMOD
 * components with proper initialization order.
 */
@Slf4j
public class FmodModule extends AbstractModule {

    @Override
    protected void configure() {
        // Bind library loader
        bind(FmodLibraryLoader.class).in(Singleton.class);

        // Bind state managers as singletons
        bind(FmodSystemStateManager.class).in(Singleton.class);
        bind(FmodSystemManager.class).in(Singleton.class);
    }

    /** Provide the FMOD library instance from the system manager. */
    @Provides
    @Singleton
    FmodLibrary provideFmodLibrary(@NonNull FmodSystemManager systemManager) {
        if (!systemManager.isInitialized()) {
            systemManager.initialize();
        }
        return systemManager.getFmodLibrary();
    }

    /** Provide the FMOD system pointer from the system manager. */
    @Provides
    @Singleton
    Pointer provideFmodSystem(@NonNull FmodSystemManager systemManager) {
        if (!systemManager.isInitialized()) {
            systemManager.initialize();
        }
        return systemManager.getSystem();
    }

    /** Provide HandleLifecycleManager as a singleton. */
    @Provides
    @Singleton
    FmodHandleLifecycleManager provideHandleLifecycleManager() {
        return new FmodHandleLifecycleManager();
    }

    /** Provide FmodAudioLoadingManager with dependencies. */
    @Provides
    @Singleton
    FmodAudioLoadingManager provideFmodAudioLoadingManager(
            @NonNull FmodLibrary fmod,
            @NonNull Pointer system,
            @NonNull FmodSystemStateManager stateManager,
            @NonNull FmodHandleLifecycleManager lifecycleManager) {
        return new FmodAudioLoadingManager(fmod, system, stateManager, lifecycleManager);
    }

    /** Provide FmodPlaybackManager with dependencies. */
    @Provides
    @Singleton
    FmodPlaybackManager provideFmodPlaybackManager(
            @NonNull FmodLibrary fmod, @NonNull Pointer system) {
        return new FmodPlaybackManager(fmod, system);
    }

    /** Provide FmodListenerManager with dependencies. */
    @Provides
    @Singleton
    FmodListenerManager provideFmodListenerManager(
            @NonNull FmodLibrary fmod, @NonNull Pointer system) {
        return new FmodListenerManager(fmod, system);
    }
}
