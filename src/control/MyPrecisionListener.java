package control;

import util.GiveMessage;
import components.MyMenu;
import components.MySplitPane;

import audio.PrecisionEvent;
import audio.PrecisionListener;

/**
 * Keeps display and actions up to date with audio playback.
 */
public class MyPrecisionListener implements PrecisionListener {
	
	private long greatestProgress;
	private long lastProgress = -1;

	public MyPrecisionListener() {
		greatestProgress = -1;
	}
	
	public void progress(long frame) {
		lastProgress = frame;
		if(frame > greatestProgress) {
			greatestProgress = frame;
		}
		CurAudio.setAudioProgressWithoutUpdatingActions(frame);
	}

	public void stateUpdated(PrecisionEvent pe) {
		PrecisionEvent.EventCode code = pe.getCode();
		switch(code) {
			case OPENED: 
				//no MyMenu.update() here because there is no corresponding CLOSED event, handled in CurAudio.switch()
				MySplitPane.getInstance().setContinuousLayout(false);
				break;
			case PLAYING:
				MyMenu.updateActions();
				break;
			case STOPPED:
				//this may be a "pause" or a StopAction, no way to tell here
				//handle stops in StopAction
				if(lastProgress > pe.getFrame()) {
					System.err.println("last progress " + lastProgress + " comes after the current pause/stop " + pe.getFrame() + ". isn't that odd?");
				}
				MyMenu.updateActions();
				break;
			case EOM:
				offerGreatestProgress(pe.getFrame());
				CurAudio.setAudioProgressAndUpdateActions(pe.getFrame());
				if(CurAudio.getAudioProgress() != CurAudio.getMaster().durationInFrames() - 1) {
					System.err.println("the frame reported by EOM event is not the final frame, violating PrecisionPlayer spec");
				}
				break;
			case ERROR:
				CurAudio.setAudioProgressAndUpdateActions(0);
				String error = "An error ocurred during audio playback.\n" + pe.getErrorMessage();
				GiveMessage.errorMessage(error);
				break;
			default:
				System.err.println("unhandled PrecisionEvent");
				break;
		}
	}
	
	public long getGreatestProgress() {
		return greatestProgress;
	}
	
	public void offerGreatestProgress(long frame) {
		if(frame > greatestProgress) {
			greatestProgress = frame;
		}
	}
}
