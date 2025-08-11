
package behaviors.singleact;

import java.awt.event.ActionEvent;

import components.MyMenu;

import control.CurAudio;
import audio.PrecisionPlayer;

/**
 * Stops audio playback.
 * 
 */
public class StopAction extends IdentifiedSingleAction {

	/**
	 * Performs the action, without saving the stopped position.
	 * 
	 * @param e The <code>ActionEvent</code> provided by the trigger
	 */
	@Override	
	public void actionPerformed(ActionEvent e) {
		super.actionPerformed(e);
		boolean currentlyPlaying = CurAudio.getPlayer().getStatus() == PrecisionPlayer.Status.PLAYING;
		CurAudio.getPlayer().stop();
		CurAudio.setAudioProgressWithoutUpdatingActions(0);
		if(currentlyPlaying == false) {
			MyMenu.updateActions();
		}
	}

	/**
	 * The user can stop audio when audio is open, playing, and not on the first frame.
	 */
	@Override
	public void update() {
		if(CurAudio.audioOpen()) {
			if(CurAudio.getPlayer().getStatus() == PrecisionPlayer.Status.PLAYING) {
				setEnabled(true);
			}
			else {
				if(CurAudio.getAudioProgress() <= 0) {
					setEnabled(false);
				}
				else {
					setEnabled(true);
				}
			}
		}
		else {
			setEnabled(false);
		}
	}
}
