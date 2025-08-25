package actions;

import control.InfoRequestedEvent;
import info.Constants;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.EventDispatchBus;

/** Attempts to bring the user to the program's tutorial website. */
@Singleton
public class VisitTutorialSiteAction extends BaseAction {
    private static final Logger logger = LoggerFactory.getLogger(VisitTutorialSiteAction.class);

    private final EventDispatchBus eventBus;

    @Inject
    public VisitTutorialSiteAction(EventDispatchBus eventBus) {
        super("Visit Tutorial Site", "Open tutorial website in browser");
        this.eventBus = eventBus;
    }

    /**
     * Attempts to guide the user's web browser to the program tutorial website.
     *
     * <p>If the user cannot be brought to the program's tutorial site in the web browser, a dialog
     * is launched containing the URL.
     */
    @Override
    protected void performAction(ActionEvent e) {
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
        // Fire info requested event - UI will handle showing the info dialog
        eventBus.publish(
                new InfoRequestedEvent(
                        Constants.tutorialSite
                                + "\n\n"
                                + Constants.orgName
                                + "\n"
                                + Constants.orgAffiliationName));
    }

    /** VisitTutorialSiteAction is always enabled. */
    @Override
    public void update() {
        // Always enabled
    }
}
