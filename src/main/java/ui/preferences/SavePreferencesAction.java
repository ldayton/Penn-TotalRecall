package ui.preferences;

import core.preferences.BadPreferenceException;
import java.awt.event.ActionEvent;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ui.DialogService;

/** Tries to save all the graphically displayed preferences. */
public class SavePreferencesAction extends AbstractAction {
    private static final Logger logger = LoggerFactory.getLogger(SavePreferencesAction.class);

    private final PreferencesFrame preferencesFrame;
    private final DialogService dialogService;

    public SavePreferencesAction(PreferencesFrame preferencesFrame, DialogService dialogService) {
        this.preferencesFrame = preferencesFrame;
        this.dialogService = dialogService;
    }

    /**
     * Recurses through all <code>AbstractPreferenceDisplays</code>, calling {@link
     * AbstractPreferenceDisplay#save()}.
     *
     * <p>If no exceptions are generated, hides <code>PreferenceFrame</code>. Otherwise, {@link
     * BadPreferenceException}s generated are stored and displayed as a batch to the user, with the
     * <code>PreferenceFrame</code> remaining open.
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        List<AbstractPreferenceDisplay> allPrefs = preferencesFrame.getAbstractPreferences();
        boolean closeWindow = true;
        List<BadPreferenceException> errorMessages = new ArrayList<>();
        for (int i = 0; i < allPrefs.size(); i++) {
            try {
                allPrefs.get(i).save();
            } catch (BadPreferenceException e1) {
                closeWindow = false;
                allPrefs.get(i).graphicallyRevert();
                errorMessages.add(e1);
                logger.error("Error saving preferences", e1);
            }
        }
        if (closeWindow) {
            // safer than directly using setVisible(false), since this double checks the assumption
            // that all preferences are saved
            preferencesFrame.windowClosing(
                    new WindowEvent(preferencesFrame, WindowEvent.WINDOW_CLOSING));
        } else {
            StringBuilder bigMessage =
                    new StringBuilder("The following preferences could not be saved:\n\n");
            for (int i = 0; i < errorMessages.size(); i++) {
                bigMessage.append("-- ").append(errorMessages.get(i).getPrefName()).append(" --\n");
                bigMessage.append(errorMessages.get(i).getMessage()).append("\n");
                if (i < errorMessages.size() - 1) {
                    bigMessage.append("\n");
                }
            }
            dialogService.showError(bigMessage.toString());
            preferencesFrame.toFront(); // to make sure PreferenceFrame will be in foreground
        }
    }
}
