package audio.exceptions;

import lombok.NonNull;

/** Base exception for all audio-related errors. */
public class AudioException extends RuntimeException {

    public AudioException(@NonNull String message) {
        super(message);
    }

    public AudioException(@NonNull String message, @NonNull Throwable cause) {
        super(message, cause);
    }

    public AudioException(@NonNull Throwable cause) {
        super(cause);
    }
}
