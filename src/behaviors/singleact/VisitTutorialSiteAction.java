package behaviors.singleact;

import info.Constants;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import util.GiveMessage;

/** Attempts to bring the user to the program's tutorial website. */
public class VisitTutorialSiteAction extends IdentifiedSingleAction {

    public VisitTutorialSiteAction() {}

    /**
     * Attempts to guide the user's web browser to the program tutorial website.
     *
     * <p>If the user cannot be brought to the program's tutorial site in the web browser, a dialog
     * is launched containing the URL.
     */
    public void actionPerformed(ActionEvent e) {
        try {
            // requires Java 6 API
            if (java.awt.Desktop.isDesktopSupported()) {
                if (java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                    java.awt.Desktop.getDesktop().browse(new URI(Constants.tutorialSite));
                }
            }
            return;
        } catch (IOException e1) {
            e1.printStackTrace();
        } catch (URISyntaxException e1) {
            e1.printStackTrace();
        } catch (NoClassDefFoundError e1) {
            System.err.println("Without Java 6 your browser cannot be launched");
            e1.printStackTrace();
        }

        // in case Java 6 classes not available
        GiveMessage.infoMessage(
                Constants.tutorialSite
                        + "\n\n"
                        + Constants.orgName
                        + "\n"
                        + Constants.orgAffiliationName);
    }

    /** <code>VisitTutorialSiteAction</code> is always enabled. */
    @Override
    public void update() {}
}
