package ui.annotations;

import core.annotations.Annotation;
import core.audio.session.AudioSessionDataSource;
import core.dispatch.EventDispatchBus;
import core.dispatch.Subscribe;
import core.env.Constants;
import core.events.CloseAudioFileEvent;
import core.events.CompleteAnnotationEvent;
import core.events.DialogEvent;
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
    private final AudioSessionDataSource sessionSource;
    private final AnnotationDisplay annotationDisplay;
    private AudioFile currentAudioFile = null;

    @Inject
    public AnnotationManager(
            @NonNull EventDispatchBus eventBus,
            @NonNull AudioSessionDataSource sessionSource,
            @NonNull AnnotationDisplay annotationDisplay) {
        this.eventBus = eventBus;
        this.sessionSource = sessionSource;
        this.annotationDisplay = annotationDisplay;
        eventBus.subscribe(this);
    }

    @Subscribe
    public void onAnnotationCompleteRequested(@NonNull CompleteAnnotationEvent event) {
        log.debug("Processing annotation complete request");

        String curFileName = sessionSource.getCurrentAudioFilePath().orElse(null);
        if (curFileName == null) {
            eventBus.publish(
                    new DialogEvent("No audio file currently open.", DialogEvent.Type.ERROR));
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
                        new DialogEvent(
                                "Output file already exists. You should not be able to reach this"
                                        + " condition.",
                                DialogEvent.Type.ERROR));
                return;
            } else {
                if (!tmpFile.renameTo(oFile)) {
                    eventBus.publish(new DialogEvent("Operation failed.", DialogEvent.Type.ERROR));
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
            eventBus.publish(
                    new DialogEvent(
                            "You have not made any annotations yet.", DialogEvent.Type.ERROR));
        }
    }

    // Annotation display operations - delegating to the injected AnnotationDisplay instance

    public Annotation[] getAnnotationsInOrder() {
        return annotationDisplay.getAnnotationsInOrder();
    }

    public void addAnnotation(Annotation ann) {
        annotationDisplay.addAnnotation(ann);
    }

    public void addAnnotations(Iterable<Annotation> anns) {
        annotationDisplay.addAnnotations(anns);
    }

    public void removeAnnotation(int rowIndex) {
        annotationDisplay.removeAnnotation(rowIndex);
    }

    public void removeAllAnnotations() {
        annotationDisplay.removeAllAnnotations();
    }

    public int getNumAnnotations() {
        return annotationDisplay.getNumAnnotations();
    }

    // We'll need to track the current audio file when it's loaded
    // This would be done through AppStateChangedEvent but that requires more context
}
