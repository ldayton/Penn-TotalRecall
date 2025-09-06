package audio.exceptions;

import lombok.NonNull;

/** Thrown when audio engine configuration contains invalid values. */
public class AudioConfigException extends AudioException {

    public AudioConfigException(@NonNull String message) {
        super(message);
    }

    public AudioConfigException(@NonNull String message, @NonNull Throwable cause) {
        super(message, cause);
    }
}
