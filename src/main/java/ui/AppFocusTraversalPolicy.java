package ui;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.Component;
import java.awt.Container;
import java.awt.FocusTraversalPolicy;
import java.awt.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ui.annotations.AnnotationTable;
import ui.audiofiles.AudioFileList;
import ui.wordpool.WordpoolList;
import ui.wordpool.WordpoolTextField;

/**
 * A custom <code>FocusTraversalPolicy</code> for this program, including documentation on general
 * focus guidelines of the program.
 *
 * <p>To speed up the annotation process we generally want users to be able to use the program using
 * only the keyboard. One exception is the adding of audio files. For that you need to drag files
 * onto the program with a mouse, or use the mouse on the file chooser.
 *
 * <p>Any component in <code>MainFrame</code> that can be clicked by the user MUST handle focus
 * passing. The simplest way to do this is to add an anonymous mouse listener to a clickable
 * component that calls {@link javax.swing.JComponent#requestFocusInWindow()} on whatever component
 * it wants to pass focus to. Focus should always be in one of (1) <code>MainFrame</code>, (2)
 * <code>
 * VolumeSliderDisplay.VolumeSlider</code>, (3) <code>AudioFileList</code>, (4) <code>
 * WordpoolTextField</code>, (5) <code>WordpoolList</code>, (6) <code>AnnotationTable</code>. Other
 * <code>JComponents</code> should choose the above components to pass focus to when clicked on. For
 * example, the <code>ControlPanel</code> gives focus to <code>MainFrame</code>, and the mute button
 * gives focus to the volume slider.
 *
 * <p>Focusable components are traversed in the order given above, looping back from the last
 * component to the first.
 *
 * <p>Please keep the spreadsheet in /dev updated with changes to the focus subsystem.
 */
@Singleton
public class AppFocusTraversalPolicy extends FocusTraversalPolicy {
    private static final Logger logger = LoggerFactory.getLogger(AppFocusTraversalPolicy.class);

    // these are components that can take focus, in the order of focus traversal desired
    // must have at least one element to avoid ArrayIndexOutOfBoundsException
    private final Component[] focusLoop;

    @Inject
    public AppFocusTraversalPolicy(MainFrame myFrame, DoneButton doneButton) {
        this.focusLoop =
                new Component[] {
                    myFrame,
                    AudioFileList.getFocusTraversalReference(),
                    WordpoolTextField.getFocusTraversalReference(),
                    WordpoolList.getFocusTraversalReference(),
                    AnnotationTable.getFocusTraversalReference(),
                    doneButton
                };
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
}
