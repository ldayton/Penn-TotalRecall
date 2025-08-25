package audio;

/**
 * Handler for fast audio progress updates.
 *
 * <p>This interface is designed for high-frequency progress callbacks that occur during audio
 * playback (~30 times per second). Implementations must be lightweight to avoid audio glitches.
 *
 * <p>Progress updates are called directly in the playback thread, so they should not perform any UI
 * updates or blocking operations.
 */
public interface AudioProgressHandler {

    /**
     * Fast progress update - called directly in playback thread.
     *
     * <p>This method is called approximately 30 times per second during audio playback.
     * Implementations must be lightweight and non-blocking to avoid audio glitches.
     *
     * @param frame Current playback frame position
     */
    void updateProgress(long frame);
}
