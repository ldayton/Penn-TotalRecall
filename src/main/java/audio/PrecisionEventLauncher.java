package audio;

import java.util.List;

/**
 * Convenicne thread launcher for <code>PrecisionEvents</code> since the spec requires they be sent
 * in a different thread than audio playback.
 */
public class PrecisionEventLauncher extends Thread {

    private final long position;
    private final PrecisionEvent.EventCode code;
    private final List<PrecisionListener> listeners;
    private final String errorMessage;

    /**
     * Prepares a launcher thread with the provided parameters.
     *
     * @param code The code of the <code>PrecisionEvent</code>
     * @param position The frame at which the event occurs
     * @param listeners The listeners to be notified of the event
     */
    public PrecisionEventLauncher(
            PrecisionEvent.EventCode code,
            long position,
            String errorMessage,
            List<PrecisionListener> listeners) {
        super();
        this.position = position;
        this.code = code;
        this.listeners = listeners;
        this.errorMessage = errorMessage;
    }

    /** Notifies registered listeners of the event. */
    @Override
    public void run() {
        if (listeners != null) {
            for (PrecisionListener lis : listeners) {
                PrecisionEvent event = new PrecisionEvent(code, position, errorMessage);
                lis.stateUpdated(event);
            }
        }
    }
}
