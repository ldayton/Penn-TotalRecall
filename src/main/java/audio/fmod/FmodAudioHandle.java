package audio.fmod;

import audio.AudioHandle;
import com.sun.jna.Pointer;
import lombok.Getter;
import lombok.NonNull;

/** FMOD implementation of AudioHandle with generation-based validity tracking. */
class FmodAudioHandle implements AudioHandle {

    @Getter private final long id;
    @Getter private final long generation;
    private final Pointer sound;
    @Getter @NonNull private final String filePath;
    private final FmodHandleLifecycleManager lifecycleManager;

    FmodAudioHandle(
            long id,
            long generation,
            @NonNull Pointer sound,
            @NonNull String filePath,
            @NonNull FmodHandleLifecycleManager lifecycleManager) {
        this.id = id;
        this.generation = generation;
        this.sound = sound;
        this.filePath = filePath;
        this.lifecycleManager = lifecycleManager;
    }

    Pointer getSound() {
        return sound;
    }

    @Override
    public boolean isValid() {
        // Delegate validity check to the lifecycle manager
        return lifecycleManager.isValid(this);
    }
}
