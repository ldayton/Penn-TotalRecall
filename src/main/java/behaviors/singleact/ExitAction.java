package behaviors.singleact;

import control.CurAudio;
import di.GuiceBootstrap;
import env.PreferencesManager;
import info.Constants;
import info.PreferenceKeys;
import jakarta.inject.Inject;
import java.awt.event.ActionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.DialogService;

/** Exits the program in response to user request. */
public class ExitAction extends IdentifiedSingleAction {
    private static final Logger logger = LoggerFactory.getLogger(ExitAction.class);

    private final PreferencesManager preferencesManager;

    @Inject
    public ExitAction(PreferencesManager preferencesManager) {
        this.preferencesManager = preferencesManager;
    }

    /**
     * Performs exit, possibly querying user for confirmation if the inter-session preferences
     * request such warning.
     */
    public void actionPerformed(ActionEvent e) {
        // Handle case where preferencesManager might be null (during bootstrap)
        boolean shouldWarn =
                preferencesManager != null
                        ? preferencesManager.getBoolean(
                                PreferenceKeys.WARN_ON_EXIT, PreferenceKeys.DEFAULT_WARN_ON_EXIT)
                        : PreferenceKeys.DEFAULT_WARN_ON_EXIT;
        if (shouldWarn) {
            String message = "Are you sure you want to exit " + Constants.programName + "?";
            DialogService dialogService =
                    GuiceBootstrap.getRequiredInjectedInstance(
                            DialogService.class, "DialogService");
            DialogService.ConfirmationResult result =
                    dialogService.showConfirmWithDontShowAgain(message);
            if (result.isDontShowAgain() && preferencesManager != null) {
                preferencesManager.putBoolean(PreferenceKeys.WARN_ON_EXIT, false);
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
                    GuiceBootstrap.getRequiredInjectedInstance(
                            components.WindowManager.class, "WindowManager");
            components.MyFrame myFrame =
                    GuiceBootstrap.getRequiredInjectedInstance(components.MyFrame.class, "MyFrame");
            components.MySplitPane mySplitPane =
                    GuiceBootstrap.getRequiredInjectedInstance(
                            components.MySplitPane.class, "MySplitPane");

            windowManager.saveWindowLayout(myFrame, mySplitPane);
        } catch (Exception e) {
            logger.warn("Failed to save window layout during exit", e);
        }

        System.exit(0);
    }
}
