package core.events;

/**
 * Audio playback event system for state change notifications.
 *
 * <h3>Event Types & Timing</h3>
 *
 * <ul>
 *   <li>OPENED: File loaded successfully (frame = -1)
 *   <li>PLAYING: Playback started (frame = start position)
 *   <li>STOPPED: User stopped playback (frame = hearing position)
 *   <li>EOM: End of media reached (frame = end position)
 *   <li>ERROR: Failure occurred (errorMessage populated)
 * </ul>
 *
 * <h3>Threading Model</h3>
 *
 * <ul>
 *   <li>onProgress(): Called in playback thread (~30fps)
 *   <li>onEvent(): Called in separate event thread
 *   <li>Event handlers should not block playback thread
 *   <li>Progress handlers must be fast to avoid audio glitches
 * </ul>
 */
public record AudioEvent(Type type, long frame, String errorMessage) {

    public enum Type {
        OPENED, // File opened successfully
        PLAYING, // Playback started
        STOPPED, // Playback stopped by user
        EOM, // End of media reached
        ERROR // Error occurred
    }

    @Override
    public String toString() {
        return switch (type) {
            case OPENED -> "File opened: " + frame;
            case PLAYING -> "Playback started: " + frame;
            case STOPPED -> "Playback stopped: " + frame;
            case EOM -> "End of media: " + frame;
            case ERROR -> "Error: " + errorMessage;
        };
    }

    /** Listener for audio playback events and progress updates. */
    public interface Listener {

        /**
         * Called during playback to indicate current frame position. Called approximately 30 times
         * per second during main playback.
         *
         * @param frame Current playback frame
         */
        void onProgress(long frame);

        /**
         * Called when audio playback state changes. Called in a separate thread from playback.
         *
         * @param event The audio event that occurred
         */
        void onEvent(AudioEvent event);
    }
}
