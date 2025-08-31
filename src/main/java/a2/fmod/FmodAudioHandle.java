package a2.fmod;

import a2.AudioHandle;
import com.sun.jna.Pointer;
import lombok.Getter;
import lombok.NonNull;

/** FMOD implementation of AudioHandle. */
class FmodAudioHandle implements AudioHandle {

    @Getter private final long id;
    private final Pointer sound;
    @Getter @NonNull private final String filePath;

    FmodAudioHandle(long id, @NonNull Pointer sound, @NonNull String filePath) {
        this.id = id;
        this.sound = sound;
        this.filePath = filePath;
    }

    Pointer getSound() {
        return sound;
    }

    @Override
    public boolean isValid() {
        // In simplified version, handle is always valid until engine closes
        return sound != null;
    }
}
