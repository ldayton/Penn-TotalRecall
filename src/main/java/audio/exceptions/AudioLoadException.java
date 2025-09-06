package audio.exceptions;

import lombok.NonNull;

/** Exception thrown when loading audio files fails. */
public class AudioLoadException extends AudioException {

    public AudioLoadException(@NonNull String message) {
        super(message);
    }

    public AudioLoadException(@NonNull String message, @NonNull Throwable cause) {
        super(message, cause);
    }

    public AudioLoadException(@NonNull Throwable cause) {
        super(cause);
    }
}
