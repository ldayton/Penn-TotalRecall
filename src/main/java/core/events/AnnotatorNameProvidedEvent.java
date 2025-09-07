package core.events;

/**
 * Event for when annotator name is provided. This allows annotation actions to continue with the
 * provided name.
 */
public class AnnotatorNameProvidedEvent {
    private final String annotatorName;
    private final String callbackActionId;

    public AnnotatorNameProvidedEvent(String annotatorName, String callbackActionId) {
        this.annotatorName = annotatorName;
        this.callbackActionId = callbackActionId;
    }

    public String getAnnotatorName() {
        return annotatorName;
    }

    public String getCallbackActionId() {
        return callbackActionId;
    }
}
