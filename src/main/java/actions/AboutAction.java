package actions;

import env.Constants;
import env.ProgramName;
import env.ProgramVersion;
import events.EventDispatchBus;
import events.InfoRequestedEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.ActionEvent;

/** Displays information about the program to the user */
@Singleton
public class AboutAction extends BaseAction {

    private final EventDispatchBus eventBus;
    private final ProgramName programName;
    private final ProgramVersion programVersion;

    @Inject
    public AboutAction(
            EventDispatchBus eventBus, ProgramName programName, ProgramVersion programVersion) {
        super("About", "Display information about the program");
        this.eventBus = eventBus;
        this.programName = programName;
        this.programVersion = programVersion;
    }

    @Override
    protected void performAction(ActionEvent e) {
        // Fire info requested event - UI will handle showing the info dialog
        eventBus.publish(
                new InfoRequestedEvent(
                        buildAboutMessage(programName.toString(), programVersion.toString())));
    }

    /**
     * Builds the about message for the application.
     *
     * @param appName the application name
     * @param appVersion the application version
     * @return the formatted about message
     */
    public static String buildAboutMessage(String appName, String appVersion) {
        return appName
                + " v"
                + appVersion
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
}
