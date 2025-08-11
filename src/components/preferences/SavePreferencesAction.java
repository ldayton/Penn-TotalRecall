package components.preferences;

import java.awt.event.ActionEvent;
import java.awt.event.WindowEvent;
import java.util.ArrayList;

import javax.swing.AbstractAction;

import util.GiveMessage;

/**
 * Tries to save all the graphically displayed preferences.
 * 
 */
public class SavePreferencesAction extends AbstractAction {

	/**
	 * Recurses through all <code>AbstractPreferenceDisplays</code>, calling {@link AbstractPreferenceDisplay#save()}.
	 * 
	 * If no exceptions are generated, hides <code>PreferenceFrame</code>.
	 * Otherwise, {@link BadPreferenceException}s generated are stored and displayed as a batch to the user, with the <code>PreferenceFrame</code> remaining open.
	 */
	public void actionPerformed(ActionEvent e) {
		ArrayList<AbstractPreferenceDisplay> allPrefs = PreferencesFrame.getInstance().getAbstractPreferences();
		boolean closeWindow = true;
		ArrayList<BadPreferenceException> errorMessages = new ArrayList<BadPreferenceException>();
		for(int i = 0; i < allPrefs.size(); i++) {
			try {
				allPrefs.get(i).save();
			} catch (BadPreferenceException e1) {
				closeWindow = false;
				allPrefs.get(i).graphicallyRevert();
				errorMessages.add(e1);
				e1.printStackTrace();
			}
		}
		if(closeWindow) {
			//safer than directly using setVisible(false), since this double checks the assumption that all preferences are saved
			PreferencesFrame.getInstance().windowClosing(new WindowEvent(PreferencesFrame.getInstance(), WindowEvent.WINDOW_CLOSING));
		}
		else {
			String bigMessage = "The following preferences could not be saved:\n\n";
			for(int i = 0; i < errorMessages.size(); i++) {
				bigMessage += "-- " + errorMessages.get(i).getPrefName() + " --\n";
				bigMessage += errorMessages.get(i).getMessage() + "\n";
				if(i < errorMessages.size() - 1) {
					bigMessage += "\n";
				}
			}
			GiveMessage.errorMessage(bigMessage);
			PreferencesFrame.getInstance().toFront(); //to make sure PreferenceFrame will be in foreground
		}
	}
}
