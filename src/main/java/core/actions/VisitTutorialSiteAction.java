package core.actions;

import core.dispatch.EventDispatchBus;
import core.env.Constants;
import core.events.OpenUrlRequestedEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/** Attempts to bring the user to the program's tutorial website. */
@Singleton
public class VisitTutorialSiteAction extends Action {

    private final EventDispatchBus eventBus;

    @Inject
    public VisitTutorialSiteAction(EventDispatchBus eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * Publishes an event to open the tutorial website.
     *
     * <p>The UI layer is responsible for actually launching the browser.
     */
    @Override
    public void execute() {
        String fallbackMessage =
                Constants.tutorialSite
                        + "\n\n"
                        + Constants.orgName
                        + "\n"
                        + Constants.orgAffiliationName;

        eventBus.publish(new OpenUrlRequestedEvent(Constants.tutorialSite, fallbackMessage));
    }

    @Override
    public boolean isEnabled() {
        return true; // Always enabled
    }

    @Override
    public String getLabel() {
        return "Visit Tutorial Site";
    }

    @Override
    public String getTooltip() {
        return "Open tutorial website in browser";
    }
}
