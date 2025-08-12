package behaviors.singleact;

import info.SysInfo;
import java.awt.event.ActionEvent;
import util.GiveMessage;

/** Displays information about the program to the user */
public class AboutAction extends IdentifiedSingleAction {

    public AboutAction() {}

    /** Performs the action using a dialog. */
    public void actionPerformed(ActionEvent e) {
        GiveMessage.infoMessage(SysInfo.sys.aboutMessage);
    }

    /** <code>AboutAction</code> is always enabled. */
    @Override
    public void update() {}
}
