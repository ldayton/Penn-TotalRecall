package audio.exceptions;

/** Thrown when an unsupported audio engine type is requested. */
public class UnsupportedAudioEngineException extends Exception {

    public UnsupportedAudioEngineException(String engineType) {
        super("Unsupported audio engine type: " + engineType);
    }

    public UnsupportedAudioEngineException(String engineType, Throwable cause) {
        super("Unsupported audio engine type: " + engineType, cause);
    }
}
