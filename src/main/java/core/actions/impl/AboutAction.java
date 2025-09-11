package core.actions.impl;

import core.actions.Action;
import core.dispatch.EventDispatchBus;
import core.env.Constants;
import core.env.ProgramName;
import core.env.ProgramVersion;
import core.events.DialogInfoEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Optional;

/** Displays information about the program to the user */
@Singleton
public class AboutAction extends Action {

    private final EventDispatchBus eventBus;
    private final ProgramName programName;
    private final ProgramVersion programVersion;

    @Inject
    public AboutAction(
            EventDispatchBus eventBus, ProgramName programName, ProgramVersion programVersion) {
        this.eventBus = eventBus;
        this.programName = programName;
        this.programVersion = programVersion;
    }

    @Override
    public void execute() {
        // Fire info requested event - UI will handle showing the info dialog
        eventBus.publish(
                new DialogInfoEvent(
                        buildAboutMessage(programName.toString(), programVersion.toString())));
    }

    @Override
    public boolean isEnabled() {
        return true; // Always enabled
    }

    @Override
    public String getLabel() {
        return "About";
    }

    @Override
    public Optional<String> getTooltip() {
        return Optional.of("Display information about the program");
    }

    /**
     * Builds the about message for the application.
     *
     * @param appName the application name
     * @param appVersion the application version
     * @return the formatted about message
     */
    public static String buildAboutMessage(String appName, String appVersion) {
        return """
        %s v%s
        Maintainer: %s

        Released by:
        %s
        %s
        %s

        License: %s
        %s\
        """
                .formatted(
                        appName,
                        appVersion,
                        Constants.maintainerEmail,
                        Constants.orgName,
                        Constants.orgAffiliationName,
                        Constants.orgHomepage,
                        Constants.license,
                        Constants.licenseSite);
    }
}
