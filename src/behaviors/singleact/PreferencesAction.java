package behaviors.singleact;

import java.awt.event.ActionEvent;


import components.preferences.PreferencesFrame;

/**
 * Launches the preferences window.
 * 
 */
public class PreferencesAction extends IdentifiedSingleAction {
	
	private static PreferencesFrame prefs;

	public PreferencesAction() {
	}
	
	/**
	 * Performs the <code>Action</code> by setting the PreferencesFrame to visible.
	 * 
	 * If this is the first call, the PreferencesFrame and internal components will actually be created.
	 */
	public void actionPerformed(ActionEvent e) {
		if(prefs == null) {
			prefs = PreferencesFrame.getInstance();
			prefs.setVisible(true);
		}
		prefs.setVisible(true);
	}
	
	/**
	 * <code>PreferencesAction</code> is always enabled.
	 */
	@Override
	public void update() {}
}
