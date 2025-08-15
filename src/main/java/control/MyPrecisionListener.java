package control;

import audio.PrecisionEvent;
import audio.PrecisionListener;
import components.MyMenu;
import components.MySplitPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.GiveMessage;

/** Keeps display and actions up to date with audio playback. */
public class MyPrecisionListener implements PrecisionListener {
    private static final Logger logger = LoggerFactory.getLogger(MyPrecisionListener.class);

    private long greatestProgress;
    private long lastProgress = -1;

    public MyPrecisionListener() {
        greatestProgress = -1;
    }

    public void progress(long frame) {
        lastProgress = frame;
        if (frame > greatestProgress) {
            greatestProgress = frame;
        }
        CurAudio.setAudioProgressWithoutUpdatingActions(frame);
    }

    public void stateUpdated(PrecisionEvent pe) {
        PrecisionEvent.EventCode code = pe.getCode();
        switch (code) {
            case OPENED:
                // no MyMenu.update() here because there is no corresponding CLOSED event, handled
                // in CurAudio.switch()
                MySplitPane.getInstance().setContinuousLayout(false);
                break;
            case PLAYING:
                MyMenu.updateActions();
                break;
            case STOPPED:
                // this may be a "pause" or a StopAction, no way to tell here
                // handle stops in StopAction
                if (lastProgress > pe.getFrame()) {
                    logger.warn(
                            "last progress {} comes after the current pause/stop {}. isn't that"
                                    + " odd?",
                            lastProgress,
                            pe.getFrame());
                }
                MyMenu.updateActions();
                break;
            case EOM:
                offerGreatestProgress(pe.getFrame());
                CurAudio.setAudioProgressAndUpdateActions(pe.getFrame());
                if (CurAudio.getAudioProgress() != CurAudio.getMaster().durationInFrames() - 1) {
                    logger.warn(
                            "the frame reported by EOM event is not the final frame, violating"
                                    + " PrecisionPlayer spec");
                }
                break;
            case ERROR:
                CurAudio.setAudioProgressAndUpdateActions(0);
                String error = "An error ocurred during audio playback.\n" + pe.getErrorMessage();
                GiveMessage.errorMessage(error);
                break;
            default:
                logger.error("unhandled PrecisionEvent: " + pe.getCode());
                break;
        }
    }

    public long getGreatestProgress() {
        return greatestProgress;
    }

    public void offerGreatestProgress(long frame) {
        if (frame > greatestProgress) {
            greatestProgress = frame;
        }
    }
}
