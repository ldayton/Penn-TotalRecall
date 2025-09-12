package core.annotations.events;

import core.annotations.AnnotationEntry;
import java.util.Objects;

/** Event published when an annotation is updated. */
public record AnnotationUpdatedEvent(AnnotationEntry oldEntry, AnnotationEntry newEntry) {
    public AnnotationUpdatedEvent {
        Objects.requireNonNull(oldEntry, "oldEntry cannot be null");
        Objects.requireNonNull(newEntry, "newEntry cannot be null");
    }
}
