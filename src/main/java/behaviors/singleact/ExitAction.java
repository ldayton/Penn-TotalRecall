package behaviors.singleact;

import components.MyFrame;
import components.MySplitPane;
import control.CurAudio;
import di.GuiceBootstrap;
import info.Constants;
import info.GUIConstants;
import info.UserPrefs;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
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

            if (dialogService != null) {
                DialogService.ConfirmationResult result =
                        dialogService.showConfirmWithDontShowAgain(message);
                if (result.isDontShowAgain()) {
                    UserPrefs.prefs.putBoolean(UserPrefs.warnExit, false);
                }
                if (result.isConfirmed()) {
                    doExit();
                }
            } else {
                // Fallback for when DI is not available
                JCheckBox checkbox = new JCheckBox(GUIConstants.dontShowAgainString);
                Object[] params = {message, checkbox};
                int response =
                        JOptionPane.showConfirmDialog(
                                MyFrame.getInstance(),
                                params,
                                GUIConstants.yesNoDialogTitle,
                                JOptionPane.YES_NO_OPTION);
                boolean dontShow = checkbox.isSelected();
                if (dontShow && response != JOptionPane.CLOSED_OPTION) {
                    UserPrefs.prefs.putBoolean(UserPrefs.warnExit, false);
                }
                if (response == JOptionPane.YES_OPTION) {
                    doExit();
                }
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

        Rectangle bounds = MyFrame.getInstance().getBounds();
        UserPrefs.prefs.putInt(UserPrefs.windowWidth, (int) bounds.getWidth());
        UserPrefs.prefs.putInt(UserPrefs.windowHeight, (int) bounds.getHeight());
        UserPrefs.prefs.putInt(UserPrefs.windowXLocation, (int) bounds.getX());
        UserPrefs.prefs.putInt(UserPrefs.windowYLocation, (int) bounds.getY());
        UserPrefs.prefs.putInt(
                UserPrefs.dividerLocation, MySplitPane.getInstance().getDividerLocation());
        UserPrefs.prefs.putBoolean(
                UserPrefs.windowMaximized,
                MyFrame.getInstance().getExtendedState() == JFrame.MAXIMIZED_BOTH);

        System.exit(0);
    }
}
