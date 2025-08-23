package components.preferences;

import di.GuiceBootstrap;
import jakarta.inject.Inject;
import java.awt.event.ActionEvent;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.DialogService;

/** Tries to save all the graphically displayed preferences. */
public class SavePreferencesAction extends AbstractAction {
    private static final Logger logger = LoggerFactory.getLogger(SavePreferencesAction.class);

    private final PreferencesFrame preferencesFrame;

    @Inject
    public SavePreferencesAction(PreferencesFrame preferencesFrame) {
        this.preferencesFrame = preferencesFrame;
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
            String bigMessage = "The following preferences could not be saved:\n\n";
            for (int i = 0; i < errorMessages.size(); i++) {
                bigMessage += "-- " + errorMessages.get(i).getPrefName() + " --\n";
                bigMessage += errorMessages.get(i).getMessage() + "\n";
                if (i < errorMessages.size() - 1) {
                    bigMessage += "\n";
                }
            }
            DialogService dialogService = GuiceBootstrap.getInjectedInstance(DialogService.class);
            if (dialogService == null) {
                throw new IllegalStateException("DialogService not available via DI");
            }
            dialogService.showError(bigMessage);
            preferencesFrame.toFront(); // to make sure PreferenceFrame will be in foreground
        }
    }
}
