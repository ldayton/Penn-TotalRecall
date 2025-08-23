package control;

/**
 * Event for requesting annotator name input. This eliminates direct UI control from annotation
 * actions.
 */
public class AnnotatorNameRequestedEvent {
    private final String callbackActionId;

    public AnnotatorNameRequestedEvent(String callbackActionId) {
        this.callbackActionId = callbackActionId;
    }

    public String getCallbackActionId() {
        return callbackActionId;
    }
}
