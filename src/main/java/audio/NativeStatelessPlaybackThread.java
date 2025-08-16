package audio;

import behaviors.UpdatingAction;
import info.Constants;
import java.io.File;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NativeStatelessPlaybackThread extends Thread {
    private static final Logger logger =
            LoggerFactory.getLogger(NativeStatelessPlaybackThread.class);

    private final long startFrame;
    private final long endFrame;
    private final List<PrecisionListener> listeners;
    private final NativeStatelessPlayer myPlayer;
    private final File audioFile;
    private final FmodCore myLib;

    private volatile boolean finish;

    protected NativeStatelessPlaybackThread(
            FmodCore lib,
            NativeStatelessPlayer player,
            File file,
            long startFrame,
            long endFrame,
            List<PrecisionListener> listeners) {
        this.audioFile = file;
        this.listeners = listeners;
        this.startFrame = startFrame;
        this.endFrame = endFrame;
        this.myPlayer = player;
        this.myLib = lib;
        this.finish = false;
    }

    @Override
    public void run() {
        try {
            //			System.out.println(getClass().getName() + ": " + startFrame + " to " + endFrame);
            int returnCode = myLib.startPlayback(audioFile.getAbsolutePath(), startFrame, endFrame);

            if (returnCode < 0) {
                myLib.stopPlayback();
                String message = "Unable to start playback.\n";
                switch (returnCode) {
                    case (-2):
                        message += "No audio device found.";
                        break;
                    case (-3):
                        message += "Unable to find or open file.";
                        break;
                    case (-4):
                        message += "Inconsistent state. Trying to repair";
                        break;
                    case (-5):
                        message += "I/O error.";
                        break;
                    default:
                        String os = System.getProperty("os.name");
                        if (os != null && os.toLowerCase().contains("linux")) {
                            message +=
                                    "\n"
                                            + Constants.programName
                                            + " prefers exclusive access to the sound system.\n"
                                            + "Please close all sound-emitting programs and web"
                                            + " pages and try again.";
                        } else {
                            message += "Unspecified error.";
                        }
                        break;
                }
                if (listeners != null) {
                    myPlayer.setStatus(PrecisionPlayer.Status.READY);
                    PrecisionEventLauncher trigger =
                            new PrecisionEventLauncher(
                                    PrecisionEvent.EventCode.ERROR, -1, message, listeners);
                    trigger.start();
                    return;
                }
            }

            while (finish == false) {
                long framesElapsed = myLib.streamPosition();
                long curFrame = framesElapsed + startFrame;
                if (curFrame >= endFrame) {
                    if (curFrame > Integer.MAX_VALUE) {
                        // apparently this is a result of FMOD code currently not self-stopping,
                        // Issue 11
                        logger.debug("applying FMOD last-frame-is-huge workaround");
                        curFrame = endFrame;
                    } else {
                        stopPlayback();
                    }
                }

                if (listeners != null) {
                    if (framesElapsed > 0) {
                        for (PrecisionListener ppl : listeners) {
                            ppl.progress(curFrame);
                        }
                    }
                }
                if (myLib.playbackInProgress() == false) {
                    break;
                }
                try {
                    UpdatingAction.getStamps().add(System.currentTimeMillis());
                    Thread.sleep(30);
                } catch (InterruptedException e) {
                    logger.debug("Sleep interrupted during playback thread", e);
                }
            }
            if (finish == false) {
                myLib.stopPlayback(); // this is EOM. we must still call stopPlayback() to close
                // the native stream
                if (listeners != null) {
                    // there is no way to guarantee the hearing frame at this line is actually the
                    // final frame
                    // however, PrecisionPlayer requires EOM events report that they occur at the
                    // final frame, so we oblige
                    myPlayer.setStatus(PrecisionPlayer.Status.READY);
                    PrecisionEventLauncher trigger =
                            new PrecisionEventLauncher(
                                    PrecisionEvent.EventCode.EOM, endFrame, null, listeners);
                    trigger.start();
                }
            }
        } catch (Throwable t) {

            try {
                myLib.stopPlayback();
            } catch (Throwable t2) {
                logger.error("Error during playback cleanup", t2);
            }

            if (listeners != null) {
                myPlayer.setStatus(PrecisionPlayer.Status.READY);
                PrecisionEventLauncher trigger =
                        new PrecisionEventLauncher(
                                PrecisionEvent.EventCode.ERROR, -1, t.getMessage(), listeners);
                trigger.start();
                logger.error("Playback thread encountered error", t);
            }
        }

        if (myLib.playbackInProgress()) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {

            }
        }
    }

    protected long stopPlayback() {
        finish = true;
        long stopFrame = myLib.stopPlayback();
        return stopFrame;
    }
}
