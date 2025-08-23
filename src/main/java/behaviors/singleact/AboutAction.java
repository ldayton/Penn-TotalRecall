package behaviors.singleact;

import di.GuiceBootstrap;
import info.Constants;
import java.awt.event.ActionEvent;
import util.DialogService;

/** Displays information about the program to the user */
public class AboutAction extends IdentifiedSingleAction {

    public AboutAction() {}

    /** Performs the action using a dialog. */
    public void actionPerformed(ActionEvent e) {
        DialogService dialogService = GuiceBootstrap.getInjectedInstance(DialogService.class);
        if (dialogService != null) {
            dialogService.showInfo(buildAboutMessage());
        }
    }

    /**
     * Builds the about message for the application.
     *
     * @return the formatted about message
     */
    public static String buildAboutMessage() {
        return Constants.programName
                + " v"
                + Constants.programVersion
                + "\n"
                + "Maintainer: "
                + Constants.maintainerEmail
                + "\n\n"
                + "Released by:\n"
                + Constants.orgName
                + "\n"
                + Constants.orgAffiliationName
                + "\n"
                + Constants.orgHomepage
                + "\n\n"
                + "License: "
                + Constants.license
                + "\n"
                + Constants.licenseSite;
    }

    /** <code>AboutAction</code> is always enabled. */
    @Override
    public void update() {}
}
