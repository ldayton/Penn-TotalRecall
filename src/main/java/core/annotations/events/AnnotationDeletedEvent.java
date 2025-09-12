package core.annotations.events;

import core.annotations.AnnotationEntry;
import java.util.Objects;

/** Event published when an annotation is deleted. */
public record AnnotationDeletedEvent(AnnotationEntry entry) {
    public AnnotationDeletedEvent {
        Objects.requireNonNull(entry, "entry cannot be null");
    }
}
