package behaviors.singleact;

import env.Environment;
import java.awt.event.ActionEvent;
import util.GiveMessage;

/** Displays information about the program to the user */
public class AboutAction extends IdentifiedSingleAction {

    public AboutAction() {}

    /** Performs the action using a dialog. */
    public void actionPerformed(ActionEvent e) {
        GiveMessage.infoMessage(new Environment().getAboutMessage());
    }

    /** <code>AboutAction</code> is always enabled. */
    @Override
    public void update() {}
}
