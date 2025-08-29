package ui;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Component;
import java.lang.reflect.Method;
import javax.swing.JButton;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AppFocusTraversalPolicy")
class AppFocusTraversalPolicyTest {

    private Component[] testComponents;

    @BeforeEach
    void setUp() {
        // Create test components
        testComponents =
                new Component[] {
                    new JButton("Button1"),
                    new JButton("Button2"),
                    new JButton("Button3"),
                    new JButton("Button4")
                };
    }

    @SuppressWarnings("unchecked")
    private <T> T findNextEligible(
            T[] array,
            int currentIndex,
            boolean forward,
            java.util.function.Predicate<T> isEligible) {
        try {
            Method method =
                    AppFocusTraversalPolicy.class.getDeclaredMethod(
                            "findNextEligible",
                            Object[].class,
                            int.class,
                            boolean.class,
                            java.util.function.Predicate.class);
            method.setAccessible(true);
            return (T) method.invoke(null, array, currentIndex, forward, isEligible);
        } catch (Exception e) {
            throw new RuntimeException("Failed to call findNextEligible method", e);
        }
    }

    @Test
    @DisplayName("should find next eligible component in forward direction")
    void shouldFindNextEligibleComponentForward() {
        Component result = findNextEligible(testComponents, 1, true, Component::isEnabled);

        assertEquals(testComponents[2], result);
    }

    @Test
    @DisplayName("should find next eligible component in backward direction")
    void shouldFindNextEligibleComponentBackward() {
        Component result = findNextEligible(testComponents, 1, false, Component::isEnabled);

        assertEquals(testComponents[0], result);
    }

    @Test
    @DisplayName("should wrap around when reaching end of array forward")
    void shouldWrapAroundForward() {
        Component result = findNextEligible(testComponents, 3, true, Component::isEnabled);

        assertEquals(testComponents[0], result);
    }

    @Test
    @DisplayName("should wrap around when reaching beginning of array backward")
    void shouldWrapAroundBackward() {
        Component result = findNextEligible(testComponents, 0, false, Component::isEnabled);

        assertEquals(testComponents[3], result);
    }

    @Test
    @DisplayName("should skip disabled components")
    void shouldSkipDisabledComponents() {
        testComponents[2].setEnabled(false);

        Component result = findNextEligible(testComponents, 1, true, Component::isEnabled);

        assertEquals(testComponents[3], result);
    }

    @Test
    @DisplayName("should skip invisible components")
    void shouldSkipInvisibleComponents() {
        testComponents[2].setVisible(false);

        Component result =
                findNextEligible(
                        testComponents,
                        1,
                        true,
                        c -> c.isEnabled() && c.isVisible() && c.isFocusable());

        assertEquals(testComponents[3], result);
    }

    @Test
    @DisplayName("should skip non-focusable components")
    void shouldSkipNonFocusableComponents() {
        testComponents[2].setFocusable(false);

        Component result =
                findNextEligible(
                        testComponents,
                        1,
                        true,
                        c -> c.isEnabled() && c.isVisible() && c.isFocusable());

        assertEquals(testComponents[3], result);
    }

    @Test
    @DisplayName("should return null when no eligible components found")
    void shouldReturnNullWhenNoEligibleComponentsFound() {
        // Disable all components
        for (Component c : testComponents) {
            c.setEnabled(false);
        }

        Component result = findNextEligible(testComponents, 1, true, Component::isEnabled);

        assertNull(result);
    }

    @Test
    @DisplayName("should handle single component array")
    void shouldHandleSingleComponentArray() {
        Component[] singleComponent = {new JButton("Single")};

        Component result = findNextEligible(singleComponent, 0, true, Component::isEnabled);

        assertNull(result); // No other components to find
    }

    @Test
    @DisplayName("should handle empty array")
    void shouldHandleEmptyArray() {
        Component[] emptyArray = {};

        Component result = findNextEligible(emptyArray, 0, true, Component::isEnabled);

        assertNull(result);
    }

    @Test
    @DisplayName("should handle null array")
    void shouldHandleNullArray() {
        Component result = findNextEligible(null, 0, true, Component::isEnabled);

        assertNull(result);
    }

    @Test
    @DisplayName("should find first eligible component when multiple are disabled")
    void shouldFindFirstEligibleComponentWhenMultipleAreDisabled() {
        testComponents[2].setEnabled(false);
        testComponents[3].setEnabled(false);

        Component result = findNextEligible(testComponents, 1, true, Component::isEnabled);

        assertEquals(testComponents[0], result); // Should wrap around to first
    }

    @Test
    @DisplayName("should handle complex eligibility criteria")
    void shouldHandleComplexEligibilityCriteria() {
        // Set up complex criteria: must be enabled, visible, focusable, and have specific text
        testComponents[2].setEnabled(false);
        testComponents[3].setVisible(false);

        Component result =
                findNextEligible(
                        testComponents,
                        1,
                        true,
                        c ->
                                c.isEnabled()
                                        && c.isVisible()
                                        && c.isFocusable()
                                        && c instanceof JButton
                                        && ((JButton) c).getText().equals("Button1"));

        assertEquals(testComponents[0], result);
    }

    @Test
    @DisplayName("should work with custom predicate")
    void shouldWorkWithCustomPredicate() {
        // Custom predicate: only buttons with text containing "Button"
        Component result =
                findNextEligible(
                        testComponents,
                        1,
                        true,
                        c -> c instanceof JButton && ((JButton) c).getText().contains("Button"));

        assertEquals(testComponents[2], result);
    }

    @Test
    @DisplayName("should handle edge case with all components except current being ineligible")
    void shouldHandleEdgeCaseWithAllComponentsExceptCurrentBeingIneligible() {
        // Make all components except current ineligible
        testComponents[0].setEnabled(false);
        testComponents[2].setEnabled(false);
        testComponents[3].setEnabled(false);

        Component result = findNextEligible(testComponents, 1, true, Component::isEnabled);

        assertNull(result); // No eligible components found
    }

    @Test
    @DisplayName("should handle bidirectional traversal correctly")
    void shouldHandleBidirectionalTraversalCorrectly() {
        // Test that forward and backward give different results
        Component forwardResult = findNextEligible(testComponents, 1, true, Component::isEnabled);
        Component backwardResult = findNextEligible(testComponents, 1, false, Component::isEnabled);

        assertEquals(testComponents[2], forwardResult);
        assertEquals(testComponents[0], backwardResult);
        assertNotEquals(forwardResult, backwardResult);
    }

    @Test
    @DisplayName("should match original LoopIterator behavior exactly")
    void shouldMatchOriginalLoopIteratorBehaviorExactly() {
        // This test verifies that our findNextEligible method behaves exactly like
        // the original LoopIterator would have behaved in the focus traversal context

        // Test case 1: Forward from middle
        Component result1 = findNextEligible(testComponents, 1, true, Component::isEnabled);
        assertEquals(testComponents[2], result1);

        // Test case 2: Backward from middle
        Component result2 = findNextEligible(testComponents, 1, false, Component::isEnabled);
        assertEquals(testComponents[0], result2);

        // Test case 3: Forward from end (should wrap)
        Component result3 = findNextEligible(testComponents, 3, true, Component::isEnabled);
        assertEquals(testComponents[0], result3);

        // Test case 4: Backward from beginning (should wrap)
        Component result4 = findNextEligible(testComponents, 0, false, Component::isEnabled);
        assertEquals(testComponents[3], result4);

        // Test case 5: With disabled component in path
        testComponents[2].setEnabled(false);
        Component result5 = findNextEligible(testComponents, 1, true, Component::isEnabled);
        assertEquals(testComponents[3], result5);

        // Test case 6: With multiple disabled components (should wrap around)
        testComponents[3].setEnabled(false);
        Component result6 = findNextEligible(testComponents, 1, true, Component::isEnabled);
        assertEquals(testComponents[0], result6);

        // Test case 7: No eligible components (should return null)
        testComponents[0].setEnabled(false);
        Component result7 = findNextEligible(testComponents, 1, true, Component::isEnabled);
        assertNull(result7);
    }
}
