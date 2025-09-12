package core.annotations;

/**
 * Interface for accessing audio state information. This will be implemented by the actual
 * AudioState class when available.
 */
public interface AudioStateInterface {

    /** Checks if an audio file is currently open. */
    boolean audioOpen();

    /** Gets the current playback position in milliseconds. */
    double getCurrentTimeMillis();

    /** Gets the total duration of the audio in milliseconds. */
    double getDurationMillis();

    /** Checks if audio is currently playing. */
    boolean isPlaying();
}
