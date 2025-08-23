package control;

import audio.AudioEvent;
import components.MyMenu;
import components.MySplitPane;
import di.GuiceBootstrap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.DialogService;

/** Keeps display and actions up to date with audio playback. */
public class MyPrecisionListener implements AudioEvent.Listener {
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

    @Override
    public void onProgress(long frame) {
        progress(frame);
    }

    public void onEvent(AudioEvent event) {
        AudioEvent.Type code = event.type();
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
                if (lastProgress > event.frame()) {
                    logger.warn(
                            "last progress {} comes after the current pause/stop {}. isn't that"
                                    + " odd?",
                            lastProgress,
                            event.frame());
                }
                MyMenu.updateActions();
                break;
            case EOM:
                offerGreatestProgress(event.frame());
                CurAudio.setAudioProgressAndUpdateActions(event.frame());
                if (CurAudio.getAudioProgress() != CurAudio.getMaster().durationInFrames() - 1) {
                    logger.warn(
                            "the frame reported by EOM event is not the final frame, violating"
                                    + " AudioPlayer spec");
                }
                break;
            case ERROR:
                CurAudio.setAudioProgressAndUpdateActions(0);
                String error = "An error ocurred during audio playback.\n" + event.errorMessage();
                DialogService dialogService =
                        GuiceBootstrap.getInjectedInstance(DialogService.class);
                if (dialogService != null) {
                    dialogService.showError(error);
                }
                break;
            default:
                logger.error("unhandled AudioEvent: " + event.type());
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
