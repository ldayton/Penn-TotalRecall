package behaviors.singleact;

import di.GuiceBootstrap;
import info.Constants;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.DialogService;

/** Attempts to bring the user to the program's tutorial website. */
public class VisitTutorialSiteAction extends IdentifiedSingleAction {
    private static final Logger logger = LoggerFactory.getLogger(VisitTutorialSiteAction.class);

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
            logger.error("IO error launching browser", e1);
        } catch (URISyntaxException e1) {
            logger.error("Invalid URI syntax for tutorial site", e1);
        } catch (NoClassDefFoundError e1) {
            logger.error("Without Java 6 your browser cannot be launched", e1);
        }

        // in case Java 6 classes not available
        DialogService dialogService = GuiceBootstrap.getInjectedInstance(DialogService.class);
        if (dialogService == null) {
            throw new IllegalStateException("DialogService not available via DI");
        }
        dialogService.showInfo(
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
