package audio.exceptions;

import lombok.NonNull;

/** Thrown when attempting to load an audio file with unsupported format. */
public class UnsupportedAudioFormatException extends AudioLoadException {

    public UnsupportedAudioFormatException(@NonNull String message) {
        super(message);
    }

    public UnsupportedAudioFormatException(@NonNull String message, @NonNull Throwable cause) {
        super(message, cause);
    }
}
