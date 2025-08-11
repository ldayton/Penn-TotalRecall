package audio;

/**
 * Notification object <code>PrecisionPlayers</code> pass to their <code>PrecisionListeners</code>,
 * using <code>PrecisionListener.stateUpdated()</code>.
 *
 * <p>Audio playback progress notifications are defined in the <code>PrecisionListener</code>
 * interface. By the time an update event is created and reported, the corresponding status change
 * should already have occurred. For example, the <code>PrecisionPlayer</code>'s status should
 * switch away from <code>PLAYING</code> before the <code>STOPPED</code> event is reported.
 */
public class PrecisionEvent {

    /**
     * Type of the event <code>OPENED</code> indicates <code>PrecisionPlayer.open()</code> is done
     * executing. <code>PLAYING</code> indicates main playback is underway. Called only once per
     * <code>PrecisionPlayer.playAt()</code> call. Do NOT confuse with <code>
     * PrecisionListener.progress()</code> which is called throughout main playback. <code>STOPPED
     * </code> indicates <code>PrecisionPlayer.stop()</code> is done executing. <code>EOM</code>
     * indicates playback has reached the end of the audio media.
     */
    public static enum EventCode {
        OPENED,
        PLAYING,
        STOPPED,
        EOM,
        ERROR
    }

    private long frame;
    private String errorMessage;

    private EventCode code;

    /**
     * Constructor for Event. <code>OPENED</code> events take place at frame -1. <code>PLAYING
     * </code> events take place at frame 0. <code>EOM</code> events take place at the final frame
     * of the audio media. <code>STOPPED</code> events take place at the last "hearing frame" of
     * main playback.
     *
     * @param code The <code>EventCode</code> corresponding to this event.
     * @param frame The frame at which the event took place.
     */
    public PrecisionEvent(EventCode code, long frame, String errorMessage) {
        this.code = code;
        this.frame = frame;
        this.errorMessage = errorMessage;
    }

    /**
     * Getter for <code>PrecisionEvent</code>'s identifying code.
     *
     * @return The PrecisionEvent.EventCode that identifies this PrecisionEvent.
     */
    public EventCode getCode() {
        return code;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Getter for the frame at which this <code>PrecisionEvent</code> took place.
     *
     * @return The frame number for this event.
     */
    public long getFrame() {
        return frame;
    }

    /** Human readable string representation of event. */
    @Override
    public String toString() {
        switch (code) {
            case OPENED:
                return "FILE OPENED: " + frame;
            case PLAYING:
                return "PLAYBACK BEGUN: " + frame;
            case STOPPED:
                return "PLAYBACK STOPPED: " + frame;
            case EOM:
                return "END OF MEDIA REACHED: " + frame;
            case ERROR:
                return "ERROR: " + frame;
            default:
                return "UNKOWN EVENT CODE: " + frame;
        }
    }
}
