package audio.exceptions;

/** Thrown when audio engine configuration contains invalid values. */
public class InvalidAudioConfigException extends Exception {

    public InvalidAudioConfigException(String message) {
        super(message);
    }

    public InvalidAudioConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
