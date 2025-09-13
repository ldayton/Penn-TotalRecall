package core.audio.fmod;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import lombok.NonNull;

/** Utility helpers to query FMOD system/channel rates. */
final class FmodSystemUtil {

    private FmodSystemUtil() {}

    static int getSoftwareMixRate(@NonNull MemorySegment system) {
        try (Arena arena = Arena.ofConfined()) {
            var sampleRate = arena.allocate(ValueLayout.JAVA_INT);
            var speakerMode = arena.allocate(ValueLayout.JAVA_INT);
            var numRaw = arena.allocate(ValueLayout.JAVA_INT);
            int result =
                    core.audio.fmod.panama.FmodCore.FMOD_System_GetSoftwareFormat(
                            system, sampleRate, speakerMode, numRaw);
            if (result == FmodConstants.FMOD_OK) {
                return Math.max(1, sampleRate.get(ValueLayout.JAVA_INT, 0));
            }
        }
        return 48000; // fallback default
    }

    static int getSourceSampleRate(
            @NonNull MemorySegment system, @NonNull FmodPlaybackHandle handle) {
        try (Arena arena = Arena.ofConfined()) {
            var freq = arena.allocate(ValueLayout.JAVA_FLOAT);
            int result =
                    core.audio.fmod.panama.FmodCore.FMOD_Channel_GetFrequency(
                            handle.getChannel(), freq);
            if (result == FmodConstants.FMOD_OK) {
                float hz = freq.get(ValueLayout.JAVA_FLOAT, 0);
                return Math.max(1, Math.round(hz));
            }
        }
        return 44100; // conservative default
    }
}
