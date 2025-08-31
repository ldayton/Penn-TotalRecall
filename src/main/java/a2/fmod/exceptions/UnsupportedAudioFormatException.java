package a2.fmod.exceptions;

/** Thrown when attempting to load an audio file with unsupported format. */
public class UnsupportedAudioFormatException extends Exception {

    public UnsupportedAudioFormatException(String message) {
        super(message);
    }

    public UnsupportedAudioFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
