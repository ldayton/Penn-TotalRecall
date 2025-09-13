package core.audio.fmod;

import core.audio.exceptions.AudioEngineException;
import core.audio.exceptions.AudioPlaybackException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import lombok.NonNull;

/** Utility helpers to query FMOD system/channel rates. */
final class FmodSystemUtil {

    private FmodSystemUtil() {}

    static int getSoftwareMixRate(@NonNull MemorySegment system) throws AudioEngineException {
        try (Arena arena = Arena.ofConfined()) {
            var sampleRate = arena.allocate(ValueLayout.JAVA_INT);
            var speakerMode = arena.allocate(ValueLayout.JAVA_INT);
            var numRaw = arena.allocate(ValueLayout.JAVA_INT);
            int result =
                    core.audio.fmod.panama.FmodCore.FMOD_System_GetSoftwareFormat(
                            system, sampleRate, speakerMode, numRaw);
            if (result != FmodConstants.FMOD_OK) {
                throw FmodError.toEngineException(result, "get software mix rate");
            }
            int rate = sampleRate.get(ValueLayout.JAVA_INT, 0);
            if (rate <= 0) {
                throw new AudioEngineException("Invalid software mix rate: " + rate);
            }
            return rate;
        }
    }

    static int getSourceSampleRate(
            @NonNull MemorySegment system, @NonNull FmodPlaybackHandle handle)
            throws AudioPlaybackException {
        try (Arena arena = Arena.ofConfined()) {
            var freq = arena.allocate(ValueLayout.JAVA_FLOAT);
            int result =
                    core.audio.fmod.panama.FmodCore.FMOD_Channel_GetFrequency(
                            handle.getChannel(), freq);
            if (result != FmodConstants.FMOD_OK) {
                throw FmodError.toPlaybackException(result, "get channel frequency");
            }
            float hz = freq.get(ValueLayout.JAVA_FLOAT, 0);
            if (hz <= 0) {
                throw new AudioPlaybackException("Invalid channel frequency: " + hz);
            }
            return Math.round(hz);
        }
    }
}
