package audio;

/** Specification for the listener of <code>PrecisionPlayer</code> notifications. */
public interface PrecisionListener {

    /**
     * Indicates that main playback has reached the provided frame.
     *
     * <p>There is no guarantee as to how often progress notifications will come. Since many
     * applications give visual indications of audio progress, implementations should seek to give
     * enough progress notifications to support a satisfying video framerate, ideally ~30
     * notifications per second.
     *
     * <p>This notification is given in the same thread as main playback. If handlers take too long
     * to run, playback may be disturbed.
     */
    public void progress(long frames);

    /**
     * Indicates that one of the <code>PrecisionEvents</code> has occurred in main playback.
     *
     * <p>See <code>PrecisionEvent</code> class for documentation.
     *
     * <p>This notification is given in an independent thread. Handlers may execute lengthy code
     * without disturbing main playback.
     */
    public void stateUpdated(PrecisionEvent pe);
}
