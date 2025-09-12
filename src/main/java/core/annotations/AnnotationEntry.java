package core.annotations;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.NonNull;

/** Enhanced annotation with metadata and type information. */
public record AnnotationEntry(
        @NonNull UUID id,
        @NonNull Annotation annotation,
        @NonNull AnnotationType type,
        @NonNull Instant createdAt,
        Optional<String> annotatorName)
        implements Comparable<AnnotationEntry> {

    public AnnotationEntry {}

    /** Creates a new annotation entry with generated ID and current timestamp. */
    public static AnnotationEntry create(
            @NonNull Annotation annotation,
            @NonNull AnnotationType type,
            Optional<String> annotatorName) {
        return new AnnotationEntry(
                UUID.randomUUID(), annotation, type, Instant.now(), annotatorName);
    }

    /** Creates a new annotation entry with specific timestamp. */
    public static AnnotationEntry createWithTimestamp(
            @NonNull Annotation annotation,
            @NonNull AnnotationType type,
            Optional<String> annotatorName,
            @NonNull Instant createdAt) {
        return new AnnotationEntry(UUID.randomUUID(), annotation, type, createdAt, annotatorName);
    }

    @Override
    public int compareTo(AnnotationEntry other) {
        return annotation.compareTo(other.annotation);
    }

    /** Returns the time of the annotation in milliseconds. */
    public double time() {
        return annotation.time();
    }

    /** Returns the text of the annotation. */
    public String text() {
        return annotation.text();
    }

    /** Returns the word number of the annotation. */
    public int wordNum() {
        return annotation.wordNum();
    }

    /** Creates a copy of this entry with updated text. */
    public AnnotationEntry withText(String newText) {
        return new AnnotationEntry(
                id,
                new Annotation(annotation.time(), annotation.wordNum(), newText),
                type,
                createdAt,
                annotatorName);
    }

    /** Creates a copy of this entry with updated time. */
    public AnnotationEntry withTime(double newTime) {
        return new AnnotationEntry(
                id,
                new Annotation(newTime, annotation.wordNum(), annotation.text()),
                type,
                createdAt,
                annotatorName);
    }
}
