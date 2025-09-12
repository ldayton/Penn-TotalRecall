package core.annotations;

import core.dispatch.EventDispatchBus;
import core.events.AnnotationAddedEvent;
import core.events.AnnotationDeletedEvent;
import core.events.AnnotationUpdatedEvent;
import core.events.AnnotationsClearedEvent;
import core.events.AnnotationsLoadedEvent;
import core.state.WaveformSessionDataSource;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central service for managing annotations. Provides a unified interface for all annotation
 * operations.
 */
@Singleton
public class AnnotationService {
    private static final Logger logger = LoggerFactory.getLogger(AnnotationService.class);

    private final List<AnnotationEntry> annotations = new CopyOnWriteArrayList<>();
    private final EventDispatchBus eventBus;
    private final WaveformSessionDataSource audioState;
    private final AnnotationRepository repository;

    private String currentAnnotatorName = "";
    private Path currentFile;
    private boolean hasUnsavedChanges = false;

    @Inject
    public AnnotationService(
            EventDispatchBus eventBus,
            WaveformSessionDataSource audioState,
            AnnotationRepository repository) {
        this.eventBus = eventBus;
        this.audioState = audioState;
        this.repository = repository;
    }

    /** Sets the current annotator name for new annotations. */
    public void setAnnotatorName(String name) {
        this.currentAnnotatorName = Objects.requireNonNull(name, "name cannot be null");
    }

    /** Gets the current annotator name. */
    public String getAnnotatorName() {
        return currentAnnotatorName;
    }

    /** Adds a new annotation at the specified time. */
    public AnnotationEntry addAnnotation(String text, AnnotationType type, double time) {
        validateTime(time);
        Objects.requireNonNull(text, "text cannot be null");
        Objects.requireNonNull(type, "type cannot be null");

        // Determine word number based on type and text
        int wordNum = determineWordNumber(text, type);

        var annotation = new Annotation(time, wordNum, text);
        var entry = AnnotationEntry.create(annotation, type, currentAnnotatorName);

        // Insert in sorted order
        int index = Collections.binarySearch(annotations, entry);
        if (index < 0) {
            index = -index - 1;
        }
        annotations.add(index, entry);

        hasUnsavedChanges = true;
        eventBus.publish(new AnnotationAddedEvent(entry));
        logger.debug("Added annotation: {} at {} ms", text, time);

        return entry;
    }

    /** Adds an annotation at the current audio position. */
    public AnnotationEntry addAnnotationAtCurrentTime(String text, AnnotationType type) {
        if (!audioState.isAudioLoaded()) {
            throw new IllegalStateException("No audio file open");
        }
        double currentTime =
                audioState
                                .getPlaybackPosition()
                                .orElseThrow(
                                        () ->
                                                new IllegalStateException(
                                                        "Cannot get current playback position"))
                        * 1000;
        return addAnnotation(text, type, currentTime);
    }

    /** Deletes the specified annotation. */
    public boolean deleteAnnotation(AnnotationEntry entry) {
        Objects.requireNonNull(entry, "entry cannot be null");

        boolean removed = annotations.remove(entry);
        if (removed) {
            hasUnsavedChanges = true;
            eventBus.publish(new AnnotationDeletedEvent(entry));
            logger.debug("Deleted annotation: {} at {} ms", entry.text(), entry.time());
        }
        return removed;
    }

    /** Deletes annotation by ID. */
    public boolean deleteAnnotationById(UUID id) {
        Objects.requireNonNull(id, "id cannot be null");

        return annotations.stream()
                .filter(e -> e.id().equals(id))
                .findFirst()
                .map(this::deleteAnnotation)
                .orElse(false);
    }

    /** Updates an existing annotation's text. */
    public boolean updateAnnotation(UUID id, String newText) {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(newText, "newText cannot be null");

        for (int i = 0; i < annotations.size(); i++) {
            var entry = annotations.get(i);
            if (entry.id().equals(id)) {
                var newEntry = entry.withText(newText);
                annotations.set(i, newEntry);
                hasUnsavedChanges = true;
                eventBus.publish(new AnnotationUpdatedEvent(entry, newEntry));
                logger.debug("Updated annotation: {} -> {}", entry.text(), newText);
                return true;
            }
        }
        return false;
    }

    /** Gets the next annotation after the specified time. */
    public Optional<AnnotationEntry> getNextAnnotation(double fromTime) {
        return annotations.stream().filter(a -> a.time() > fromTime + 1.0).findFirst();
    }

    /** Gets the previous annotation before the specified time. */
    public Optional<AnnotationEntry> getPreviousAnnotation(double fromTime) {
        return annotations.stream()
                .filter(a -> a.time() < fromTime - 1.0)
                .reduce((first, second) -> second); // Get last matching
    }

    /** Gets all annotations in time order. */
    public List<AnnotationEntry> getAllAnnotations() {
        return new ArrayList<>(annotations);
    }

    /** Gets annotations within a time range. */
    public Stream<AnnotationEntry> getAnnotationsInRange(double startTime, double endTime) {
        return annotations.stream().filter(a -> a.time() >= startTime && a.time() <= endTime);
    }

    /** Gets annotation by ID. */
    public Optional<AnnotationEntry> getAnnotationById(UUID id) {
        Objects.requireNonNull(id, "id cannot be null");
        return annotations.stream().filter(e -> e.id().equals(id)).findFirst();
    }

    /** Loads annotations from a file. */
    public void loadAnnotations(Path file) {
        Objects.requireNonNull(file, "file cannot be null");

        try {
            var loaded = repository.load(file);
            annotations.clear();
            annotations.addAll(loaded);
            currentFile = file;
            hasUnsavedChanges = false;

            eventBus.publish(new AnnotationsLoadedEvent(loaded, file.toString()));
            logger.info("Loaded {} annotations from {}", loaded.size(), file);
        } catch (Exception e) {
            logger.error("Failed to load annotations from {}", file, e);
            throw new RuntimeException("Failed to load annotations: " + e.getMessage(), e);
        }
    }

    /** Saves current annotations to file. */
    public void saveAnnotations() {
        if (currentFile == null) {
            throw new IllegalStateException("No file loaded");
        }
        saveAnnotationsTo(currentFile);
    }

    /** Saves annotations to specified file. */
    public void saveAnnotationsTo(Path file) {
        Objects.requireNonNull(file, "file cannot be null");

        try {
            repository.save(annotations, file);
            currentFile = file;
            hasUnsavedChanges = false;
            logger.info("Saved {} annotations to {}", annotations.size(), file);
        } catch (Exception e) {
            logger.error("Failed to save annotations to {}", file, e);
            throw new RuntimeException("Failed to save annotations: " + e.getMessage(), e);
        }
    }

    /** Clears all annotations. */
    public void clearAnnotations() {
        annotations.clear();
        hasUnsavedChanges = false;
        currentFile = null;
        eventBus.publish(new AnnotationsClearedEvent());
        logger.debug("Cleared all annotations");
    }

    /** Checks if there are unsaved changes. */
    public boolean hasUnsavedChanges() {
        return hasUnsavedChanges;
    }

    /** Gets the current annotation file path. */
    public Optional<Path> getCurrentFile() {
        return Optional.ofNullable(currentFile);
    }

    /** Checks if an annotation can be placed at the specified time. */
    public boolean canAnnotateAt(double time) {
        if (!audioState.isAudioLoaded()) {
            return false;
        }
        validateTime(time);
        // Check for conflicts within 50ms
        return annotations.stream().noneMatch(a -> Math.abs(a.time() - time) < 50);
    }

    /** Gets the total number of annotations. */
    public int getAnnotationCount() {
        return annotations.size();
    }

    /** Gets count by annotation type. */
    public long getCountByType(AnnotationType type) {
        return annotations.stream().filter(a -> a.type() == type).count();
    }

    private void validateTime(double time) {
        if (time < 0) {
            throw new IllegalArgumentException("Time cannot be negative: " + time);
        }
        if (audioState.isAudioLoaded()) {
            double duration =
                    audioState.getTotalDuration().orElse(0.0)
                            * 1000; // Convert seconds to milliseconds
            if (time > duration) {
                throw new IllegalArgumentException(
                        "Time exceeds audio duration: " + time + " > " + duration);
            }
        }
    }

    private int determineWordNumber(String text, AnnotationType type) {
        // TODO: Integrate with wordpool to find matching word number
        // For now, return -1 for custom/intrusion, 0 for regular
        return switch (type) {
            case INTRUSION -> -1;
            case CUSTOM -> -1;
            case REGULAR -> 0; // Should look up in wordpool
        };
    }
}
