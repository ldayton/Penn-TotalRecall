package audio.exceptions;

import lombok.NonNull;

/** Exception thrown during audio playback operations. */
public class AudioPlaybackException extends AudioException {

    public AudioPlaybackException(@NonNull String message) {
        super(message);
    }

    public AudioPlaybackException(@NonNull String message, @NonNull Throwable cause) {
        super(message, cause);
    }

    public AudioPlaybackException(@NonNull Throwable cause) {
        super(cause);
    }
}
