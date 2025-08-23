package control;

import java.awt.Component;

/**
 * Event for components to publish their focus traversal references. This eliminates static method
 * dependencies in the focus traversal system.
 */
public class FocusTraversalReferenceEvent {
    private final Component component;
    private final String componentType;

    public FocusTraversalReferenceEvent(Component component, String componentType) {
        this.component = component;
        this.componentType = componentType;
    }

    public Component getComponent() {
        return component;
    }

    public String getComponentType() {
        return componentType;
    }
}
