package state;

import env.Constants;
import events.AnnotationCompleteRequestedEvent;
import events.AudioFileCloseRequestedEvent;
import events.ErrorRequestedEvent;
import events.EventDispatchBus;
import events.Subscribe;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.File;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import ui.audiofiles.AudioFile;
import ui.audiofiles.AudioFile.AudioFilePathException;
import util.OsPath;

/** Manages annotation operations including marking files complete. */
@Singleton
@Slf4j
public class AnnotationManager {

    private final EventDispatchBus eventBus;
    private final WaveformSessionDataSource sessionSource;
    private AudioFile currentAudioFile = null;

    @Inject
    public AnnotationManager(
            @NonNull EventDispatchBus eventBus, @NonNull WaveformSessionDataSource sessionSource) {
        this.eventBus = eventBus;
        this.sessionSource = sessionSource;
        eventBus.subscribe(this);
    }

    @Subscribe
    public void onAnnotationCompleteRequested(@NonNull AnnotationCompleteRequestedEvent event) {
        log.debug("Processing annotation complete request");

        String curFileName = sessionSource.getCurrentAudioFilePath().orElse(null);
        if (curFileName == null) {
            eventBus.publish(new ErrorRequestedEvent("No audio file currently open."));
            return;
        }

        File tmpFile =
                new File(
                        OsPath.basename(curFileName)
                                + "."
                                + Constants.temporaryAnnotationFileExtension);
        if (tmpFile.exists()) {
            File oFile =
                    new File(
                            OsPath.basename(tmpFile.getAbsolutePath())
                                    + "."
                                    + Constants.completedAnnotationFileExtension);
            if (oFile.exists()) {
                eventBus.publish(
                        new ErrorRequestedEvent(
                                "Output file already exists. You should not be able to reach this"
                                        + " condition."));
                return;
            } else {
                if (!tmpFile.renameTo(oFile)) {
                    eventBus.publish(new ErrorRequestedEvent("Operation failed."));
                    return;
                } else {
                    try {
                        if (currentAudioFile != null) {
                            currentAudioFile.updateDoneStatus();
                        }
                    } catch (AudioFilePathException e1) {
                        log.error("Failed to update audio file done status", e1);
                    }
                    // Close the audio file using the event system
                    eventBus.publish(new AudioFileCloseRequestedEvent());
                    log.info("Annotation marked complete and file closed");
                }
            }
        } else {
            eventBus.publish(new ErrorRequestedEvent("You have not made any annotations yet."));
        }
    }

    // We'll need to track the current audio file when it's loaded
    // This would be done through AppStateChangedEvent but that requires more context
}
