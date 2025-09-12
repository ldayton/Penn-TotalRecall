package core.events;

import core.annotations.AnnotationEntry;
import java.util.List;
import java.util.Objects;

/** Event published when annotations are loaded from a file. */
public record AnnotationsLoadedEvent(List<AnnotationEntry> annotations, String sourceFile) {
    public AnnotationsLoadedEvent {
        Objects.requireNonNull(annotations, "annotations cannot be null");
        Objects.requireNonNull(sourceFile, "sourceFile cannot be null");
        annotations = List.copyOf(annotations); // defensive copy
    }
}
