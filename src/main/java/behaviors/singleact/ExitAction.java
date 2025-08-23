package behaviors.singleact;

import control.CurAudio;
import di.GuiceBootstrap;
import info.Constants;
import info.UserPrefs;
import java.awt.event.ActionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.DialogService;

/** Exits the program in response to user request. */
public class ExitAction extends IdentifiedSingleAction {
    private static final Logger logger = LoggerFactory.getLogger(ExitAction.class);

    public ExitAction() {}

    /**
     * Performs exit, possibly querying user for confirmation if the inter-session preferences
     * request such warning.
     */
    public void actionPerformed(ActionEvent e) {
        boolean shouldWarn =
                UserPrefs.prefs.getBoolean(UserPrefs.warnExit, UserPrefs.defaultWarnExit);
        if (shouldWarn) {
            String message = "Are you sure you want to exit " + Constants.programName + "?";
            DialogService dialogService = GuiceBootstrap.getInjectedInstance(DialogService.class);

            if (dialogService == null) {
                throw new IllegalStateException("DialogService not available via DI");
            }
            DialogService.ConfirmationResult result =
                    dialogService.showConfirmWithDontShowAgain(message);
            if (result.isDontShowAgain()) {
                UserPrefs.prefs.putBoolean(UserPrefs.warnExit, false);
            }
            if (result.isConfirmed()) {
                doExit();
            }
        } else {
            doExit();
        }
    }

    /** <code>ExitAction</code> is always enabled. */
    @Override
    public void update() {}

    /*
     * Exit is accomplished by stopping audio playback (to prevent a weird audio sound of Java closing mid-playback), and storing window and divider positions.
     */
    private static void doExit() {
        try { // just to make sure we exit
            if (CurAudio.audioOpen()) {
                CurAudio.getPlayer().stop();
            }
        } catch (Throwable e) {
            logger.warn("Error stopping audio during application exit", e);
        }

        // Save window layout using WindowManager (which uses the same preference system as the rest
        // of the app)
        try {
            components.WindowManager windowManager =
                    GuiceBootstrap.getInjectedInstance(components.WindowManager.class);
            components.MyFrame myFrame =
                    GuiceBootstrap.getInjectedInstance(components.MyFrame.class);
            components.MySplitPane mySplitPane =
                    GuiceBootstrap.getInjectedInstance(components.MySplitPane.class);

            windowManager.saveWindowLayout(myFrame, mySplitPane);
        } catch (Exception e) {
            logger.warn("Failed to save window layout during exit", e);
        }

        System.exit(0);
    }
}
