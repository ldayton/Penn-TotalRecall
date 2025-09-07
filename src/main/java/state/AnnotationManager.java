package state;

import core.dispatch.EventDispatchBus;
import core.dispatch.Subscribe;
import core.env.Constants;
import core.events.CloseAudioFileEvent;
import core.events.CompleteAnnotationEvent;
import core.events.DialogErrorEvent;
import core.util.OsPath;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.File;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import ui.audiofiles.AudioFile;
import ui.audiofiles.AudioFile.AudioFilePathException;

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
    public void onAnnotationCompleteRequested(@NonNull CompleteAnnotationEvent event) {
        log.debug("Processing annotation complete request");

        String curFileName = sessionSource.getCurrentAudioFilePath().orElse(null);
        if (curFileName == null) {
            eventBus.publish(new DialogErrorEvent("No audio file currently open."));
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
                        new DialogErrorEvent(
                                "Output file already exists. You should not be able to reach this"
                                        + " condition."));
                return;
            } else {
                if (!tmpFile.renameTo(oFile)) {
                    eventBus.publish(new DialogErrorEvent("Operation failed."));
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
                    eventBus.publish(new CloseAudioFileEvent());
                    log.info("Annotation marked complete and file closed");
                }
            }
        } else {
            eventBus.publish(new DialogErrorEvent("You have not made any annotations yet."));
        }
    }

    // We'll need to track the current audio file when it's loaded
    // This would be done through AppStateChangedEvent but that requires more context
}
