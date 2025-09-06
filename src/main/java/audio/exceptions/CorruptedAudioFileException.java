package audio.exceptions;

import lombok.NonNull;

/** Thrown when attempting to load a corrupted or invalid audio file. */
public class CorruptedAudioFileException extends AudioLoadException {

    public CorruptedAudioFileException(@NonNull String message) {
        super(message);
    }

    public CorruptedAudioFileException(@NonNull String message, @NonNull Throwable cause) {
        super(message, cause);
    }
}
