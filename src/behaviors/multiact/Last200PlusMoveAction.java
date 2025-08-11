package behaviors.multiact;

import info.UserPrefs;

import java.awt.event.ActionEvent;

import behaviors.singleact.ReplayLast200MillisAction;

import components.MyMenu;

import control.CurAudio;
import audio.PrecisionPlayer;

/**
 * A combination of {@link behaviors.singleact.ReplayLast200MillisAction} and {@link behaviors.multiact.SeekAction}.
 * 
 */
public class Last200PlusMoveAction extends IdentifiedMultiAction {

	public static enum Direction {BACKWARD, FORWARD};
	
	private ReplayLast200MillisAction replayer;
	
	private int shift;
	private Direction dir;
	
	
	public Last200PlusMoveAction(Direction dir) {
		super(dir);
		this.dir = dir;
		replayer = new ReplayLast200MillisAction();		
		shift = UserPrefs.getSmallShift();
		if(dir == Direction.BACKWARD) {
			shift *= -1;
		}
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		super.actionPerformed(e);
		long curFrame = CurAudio.getAudioProgress();
		long frameShift = CurAudio.getMaster().millisToFrames(shift);
		long naivePosition = curFrame + frameShift;
		long frameLength = CurAudio.getMaster().durationInFrames();

		long finalPosition = naivePosition;

		if(naivePosition < 0) {
			finalPosition = 0;
		}
		else if(naivePosition > frameLength) {
			finalPosition = frameLength;
		}

		CurAudio.setAudioProgressWithoutUpdatingActions(finalPosition); //not using setAudioProgressAndUpdateActions() because we don't want to slow down start of playback
		CurAudio.getPlayer().queuePlayAt(finalPosition);

		replayer.actionPerformed(new ActionEvent(MyMenu.getInstance(), ActionEvent.ACTION_PERFORMED, null, System.currentTimeMillis()
				, 0));

		MyMenu.updateActions();
	}

	@Override
	public void update() {
		if(CurAudio.audioOpen()) {
			if(CurAudio.getPlayer().getStatus() == PrecisionPlayer.Status.PLAYING) {
				setEnabled(false);
			}
			else {
				if(CurAudio.getAudioProgress() <= 0) {
					if(dir == Direction.FORWARD) {
						setEnabled(true);
					}	
					else {
						setEnabled(false);
					}
				}
				else if(CurAudio.getAudioProgress() == CurAudio.getMaster().durationInFrames() - 1) {
					if(dir == Direction.FORWARD) {
						setEnabled(false);
					}	
					else {
						setEnabled(true);
					}
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

	public void updateSeekAmount() {
		shift = UserPrefs.getSmallShift();
		if(dir == Direction.BACKWARD) {
			shift *= -1;
		}
	}
}
