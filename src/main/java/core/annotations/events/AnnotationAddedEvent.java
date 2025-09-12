package core.annotations.events;

import core.annotations.AnnotationEntry;
import java.util.Objects;

/** Event published when an annotation is added. */
public record AnnotationAddedEvent(AnnotationEntry entry) {
    public AnnotationAddedEvent {
        Objects.requireNonNull(entry, "entry cannot be null");
    }
}
