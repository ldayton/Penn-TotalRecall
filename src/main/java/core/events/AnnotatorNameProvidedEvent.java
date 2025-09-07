package core.events;

import lombok.NonNull;

/**
 * Event for when annotator name is provided. This allows annotation actions to continue with the
 * provided name.
 */
public record AnnotatorNameProvidedEvent(
        @NonNull String annotatorName, @NonNull String callbackActionId) {}
