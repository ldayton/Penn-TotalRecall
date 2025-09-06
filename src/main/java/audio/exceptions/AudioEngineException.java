package audio.exceptions;

import lombok.NonNull;

/** Exception for audio engine initialization and state errors. */
public class AudioEngineException extends AudioException {

    public AudioEngineException(@NonNull String message) {
        super(message);
    }

    public AudioEngineException(@NonNull String message, @NonNull Throwable cause) {
        super(message, cause);
    }

    public AudioEngineException(@NonNull Throwable cause) {
        super(cause);
    }
}
