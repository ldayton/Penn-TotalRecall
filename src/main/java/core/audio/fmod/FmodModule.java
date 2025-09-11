package core.audio.fmod;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import java.lang.foreign.MemorySegment;
import lombok.NonNull;

/**
 * Guice module for FMOD audio system (Panama-based implementation). Binds and provides the FMOD
 * components without JNA.
 */
public class FmodModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(FmodLibraryLoader.class).in(Singleton.class);
        bind(FmodSystemStateManager.class).in(Singleton.class);
        bind(FmodHandleLifecycleManager.class).in(Singleton.class);
        bind(FmodSystemManager.class).in(Singleton.class);
    }

    @Provides
    @Singleton
    MemorySegment provideFmodSystemPointer(@NonNull FmodSystemManager systemManager) {
        if (!systemManager.isInitialized()) {
            systemManager.initialize();
        }
        return systemManager.getSystem();
    }

    @Provides
    @Singleton
    FmodAudioLoadingManager provideFmodAudioLoadingManager(
            @NonNull MemorySegment fmodSystemPointer,
            @NonNull FmodSystemStateManager stateManager,
            @NonNull FmodHandleLifecycleManager lifecycleManager) {
        return new FmodAudioLoadingManager(fmodSystemPointer, stateManager, lifecycleManager);
    }

    @Provides
    @Singleton
    FmodPlaybackManager provideFmodPlaybackManager(@NonNull MemorySegment fmodSystemPointer) {
        return new FmodPlaybackManager(fmodSystemPointer);
    }

    @Provides
    @Singleton
    FmodListenerManager provideFmodListenerManager(@NonNull MemorySegment fmodSystemPointer) {
        return new FmodListenerManager(fmodSystemPointer);
    }
}
