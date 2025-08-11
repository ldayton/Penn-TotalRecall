package behaviors.singleact;

import java.awt.event.ActionEvent;
import util.CheckUpdatesThread;

/** Launches a {@link util.CheckUpdatesThread}. */
public class CheckUpdatesAction extends IdentifiedSingleAction {

    private boolean informEitherWay;

    /**
     * Creates an instance of the <code>Action</code>.
     *
     * @param informEitherWay Whether or not to inform the user if an update is NOT available
     */
    public CheckUpdatesAction(boolean informEitherWay) {
        this.informEitherWay = informEitherWay;
    }

    /** Performs the <code>Action</code> by creating and launching a CheckUpdatesThread. */
    public void actionPerformed(ActionEvent e) {
        new Thread(new CheckUpdatesThread(informEitherWay)).start();
    }

    /** <code>CheckUpdatesAction</code> is always enabled. */
    @Override
    public void update() {}
}
