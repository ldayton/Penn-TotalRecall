package components;

import control.FocusTraversalReferenceEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.Component;
import java.awt.Container;
import java.awt.FocusTraversalPolicy;
import java.awt.Window;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.EventBus;
import util.Subscribe;

/**
 * A custom <code>FocusTraversalPolicy</code> for this program, including documentation on general
 * focus guidelines of the program.
 *
 * <p>To speed up the annotation process we generally want users to be able to use the program using
 * only the keyboard. One exception is the adding of audio files. For that you need to drag files
 * onto the program with a mouse, or use the mouse on the file chooser.
 *
 * <p>Any component in <code>MyFrame</code> that can be clicked by the user MUST handle focus
 * passing. The simplest way to do this is to add an anonymous mouse listener to a clickable
 * component that calls {@link javax.swing.JComponent#requestFocusInWindow()} on whatever component
 * it wants to pass focus to. Focus should always be in one of (1) <code>MyFrame</code>, (2) <code>
 * VolumeSliderDisplay.VolumeSlider</code>, (3) <code>AudioFileList</code>, (4) <code>
 * WordpoolTextField</code>, (5) <code>WordpoolList</code>, (6) <code>AnnotationTable</code>. Other
 * <code>JComponents</code> should choose the above components to pass focus to when clicked on. For
 * example, the <code>ControlPanel</code> gives focus to <code>MyFrame</code>, and the mute button
 * gives focus to the volume slider.
 *
 * <p>Focusable components are traversed in the order given above, looping back from the last
 * component to the first.
 *
 * <p>Please keep the spreadsheet in /dev updated with changes to the focus subsystem.
 */
@Singleton
public class MyFocusTraversalPolicy extends FocusTraversalPolicy {
    private static final Logger logger = LoggerFactory.getLogger(MyFocusTraversalPolicy.class);

    private static final String genericFailureMessage =
            "can't find a focus-appropriate component to give focus to";

    private final MyFrame myFrame;
    private final DoneButton doneButton;
    private final EventBus eventBus;

    // these are components that can take focus, in the order of focus traversal desired
    // must have at least one element to avoid ArrayIndexOutOfBoundsException
    private Component[] focusLoop;
    private final Map<String, Component> focusComponents = new HashMap<>();

    @Inject
    public MyFocusTraversalPolicy(MyFrame myFrame, DoneButton doneButton, EventBus eventBus) {
        this.myFrame = myFrame;
        this.doneButton = doneButton;
        this.eventBus = eventBus;

        // Initialize with basic components, others will be added via events
        this.focusLoop = new Component[] {myFrame, doneButton};

        // Subscribe to focus traversal reference events
        eventBus.subscribe(this);

        // Request focus traversal references from components
        eventBus.publish(new FocusTraversalReferenceEvent(null, "REQUEST_ALL"));
    }

    /** Returns the next component in the focus traversal loop. {@inheritDoc} */
    @Override
    public Component getComponentAfter(Container aContainer, Component aComponent) {
        return getNextComponent(aComponent, true);
    }

    /** Returns the previous component in the focus traversal loop. {@inheritDoc} */
    @Override
    public Component getComponentBefore(Container aContainer, Component aComponent) {
        return getNextComponent(aComponent, false);
    }

    /** Returns the first component in the focus traversal list. {@inheritDoc} */
    @Override
    public Component getDefaultComponent(Container aContainer) {
        return focusLoop[0];
    }

    /** Returns the first component in the focus traversal list. {@inheritDoc} */
    @Override
    public Component getInitialComponent(Window window) {
        return focusLoop[0];
    }

    /** Returns the first component in the focus traversal list. {@inheritDoc} */
    @Override
    public Component getFirstComponent(Container aContainer) {
        return focusLoop[0];
    }

    /** Returns the last component in the focus traversal list. {@inheritDoc} */
    @Override
    public Component getLastComponent(Container aContainer) {
        return focusLoop[focusLoop.length - 1];
    }

    /**
     * Handles the job of finding the next/previous component in the loop by using a {@link
     * util.FocusTraversalHelper}. Makes sure that the next component in the focus traversal cycle
     * is actually eligible for focus (i.e., enabled, visible, focusable).
     *
     * @param aComponent The base component whose successor/predecessor is to be found
     * @param forward <code>true</code> iff the direction of traversal is forward
     * @return The next focus-eligible component in the provided direction
     */
    private Component getNextComponent(Component aComponent, boolean forward) {
        int componentIndex = -1;
        for (int i = 0; i < focusLoop.length; i++) {
            Component fc = focusLoop[i];
            if (fc != aComponent) {
                continue;
            } else {
                componentIndex = i;
                break;
            }
        }
        if (componentIndex < 0) {
            logger.error(
                    "can't find the next focus component because I don't recognize the current one:"
                            + " "
                            + aComponent);
        }
        return findNextEligible(
                focusLoop,
                componentIndex,
                forward,
                c -> c.isEnabled() && c.isVisible() && c.isFocusable());
    }

    /**
     * Finds the next eligible element in a circular array traversal.
     *
     * @param array The array to search through
     * @param currentIndex The current position (will be skipped)
     * @param forward Whether to search forward (true) or backward (false)
     * @param isEligible Predicate to test if an element is eligible
     * @return The first eligible element found, or null if none found
     */
    private static <T> T findNextEligible(
            T[] array,
            int currentIndex,
            boolean forward,
            java.util.function.Predicate<T> isEligible) {
        if (array == null || array.length == 0) {
            return null;
        }

        // Calculate starting index (skip current element)
        int startIndex =
                forward
                        ? (currentIndex + 1) % array.length
                        : (currentIndex - 1 + array.length) % array.length;

        // Search through all elements except the current one
        for (int i = 0; i < array.length - 1; i++) {
            int index =
                    forward
                            ? (startIndex + i) % array.length
                            : (startIndex - i + array.length) % array.length;

            if (isEligible.test(array[index])) {
                return array[index];
            }
        }
        return null;
    }

    @Subscribe
    public void handleFocusTraversalReference(FocusTraversalReferenceEvent event) {
        if ("REQUEST_ALL".equals(event.getComponentType())) {
            // This is a request for all components to publish their references
            // Components will respond by publishing their own events
            return;
        }

        // Store the component reference
        focusComponents.put(event.getComponentType(), event.getComponent());

        // Rebuild the focus loop with all available components
        rebuildFocusLoop();
    }

    private void rebuildFocusLoop() {
        // Define the desired order of focus traversal
        String[] componentOrder = {
            "MyFrame",
            "AudioFileList",
            "WordpoolTextField",
            "WordpoolList",
            "AnnotationTable",
            "DoneButton"
        };

        // Build the focus loop in the desired order
        java.util.List<Component> orderedComponents = new java.util.ArrayList<>();

        for (String componentType : componentOrder) {
            Component component = focusComponents.get(componentType);
            if (component != null) {
                orderedComponents.add(component);
            }
        }

        // Convert to array
        focusLoop = orderedComponents.toArray(new Component[0]);
    }
}
