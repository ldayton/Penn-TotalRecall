package a2.fmod.exceptions;

/** Thrown when attempting to load a corrupted or invalid audio file. */
public class CorruptedAudioFileException extends Exception {

    public CorruptedAudioFileException(String message) {
        super(message);
    }

    public CorruptedAudioFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
