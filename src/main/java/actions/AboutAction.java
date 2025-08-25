package actions;

import env.Constants;
import events.EventDispatchBus;
import events.InfoRequestedEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.ActionEvent;

/** Displays information about the program to the user */
@Singleton
public class AboutAction extends BaseAction {

    private final EventDispatchBus eventBus;

    @Inject
    public AboutAction(EventDispatchBus eventBus) {
        super("About", "Display information about the program");
        this.eventBus = eventBus;
    }

    @Override
    protected void performAction(ActionEvent e) {
        // Fire info requested event - UI will handle showing the info dialog
        eventBus.publish(new InfoRequestedEvent(buildAboutMessage()));
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

    /** AboutAction is always enabled. */
    @Override
    public void update() {
        // Always enabled
    }
}
