package audio.fmod;

import audio.exceptions.AudioEngineException;
import audio.exceptions.AudioLoadException;
import audio.exceptions.AudioPlaybackException;
import audio.exceptions.CorruptedAudioFileException;
import audio.exceptions.UnsupportedAudioFormatException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;

/**
 * Centralized FMOD error handling utilities. Provides readable names for FMOD result codes and
 * helpers to convert them into appropriate exceptions/messages by context.
 */
@UtilityClass
class FmodError {

    private static final Map<Integer, String> CODE_NAME_MAP = buildCodeNameMap();

    private static Map<Integer, String> buildCodeNameMap() {
        Map<Integer, String> map = new HashMap<>();
        // Reflect over FmodConstants to find FMOD_ERR_* names and their values
        for (Field f : FmodConstants.class.getDeclaredFields()) {
            String name = f.getName();
            if (!name.startsWith("FMOD_ERR_") && !name.equals("FMOD_OK")) continue;
            if (!f.getType().equals(int.class)) continue;
            try {
                int value = f.getInt(null);
                map.put(value, name);
            } catch (IllegalAccessException ignored) {
            }
        }
        return map;
    }

    /** Return a readable constant name for the FMOD result code, or "UNKNOWN" if not found. */
    static String nameOf(int code) {
        return CODE_NAME_MAP.getOrDefault(code, "UNKNOWN");
    }

    /** Return a formatted description like "FMOD_ERR_INVALID_HANDLE (30)". */
    static String describe(int code) {
        return nameOf(code) + " (" + code + ")";
    }

    /** Build an AudioLoadException appropriate for the FMOD error code. */
    static AudioLoadException toLoadException(int code, String filePath) {
        return switch (code) {
            case FmodConstants.FMOD_ERR_FILE_NOTFOUND ->
                    new AudioLoadException("Audio file not found: " + filePath);
            case FmodConstants.FMOD_ERR_FORMAT ->
                    new UnsupportedAudioFormatException("Unsupported audio format: " + filePath);
            case FmodConstants.FMOD_ERR_FILE_BAD ->
                    new CorruptedAudioFileException("Corrupted or invalid audio file: " + filePath);
            case FmodConstants.FMOD_ERR_MEMORY ->
                    new AudioLoadException("Insufficient memory to load audio file: " + filePath);
            default ->
                    new AudioLoadException(
                            "Failed to load audio file '"
                                    + filePath
                                    + "' ("
                                    + describe(code)
                                    + ")");
        };
    }

    /** Build an AudioPlaybackException for a playback operation failure. */
    static AudioPlaybackException toPlaybackException(int code, String action) {
        return new AudioPlaybackException("Failed to " + action + ": " + describe(code));
    }

    /** Build an AudioEngineException for a system/lifecycle operation failure. */
    static AudioEngineException toEngineException(int code, String action) {
        return new AudioEngineException("Failed to " + action + ": " + describe(code));
    }
}
