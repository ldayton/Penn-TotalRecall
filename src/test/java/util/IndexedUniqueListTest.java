package util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("IndexedUniqueList")
class IndexedUniqueListTest {

    private IndexedUniqueList<String> list;

    @BeforeEach
    void setUp() {
        list = new IndexedUniqueList<>();
    }

    @Nested
    @DisplayName("Core Specification")
    class CoreSpecification {

        @Test
        @DisplayName("should maintain uniqueness while preserving insertion order")
        void shouldMaintainUniquenessWhilePreservingInsertionOrder() {
            // Core specification: uniqueness + insertion order
            list.add("first");
            list.add("second");
            list.add("first"); // Duplicate should be rejected

            assertEquals(2, list.size());
            assertEquals("first", list.get(0));
            assertEquals("second", list.get(1));
        }

        @Test
        @DisplayName("should provide O(1) index access")
        void shouldProvideO1IndexAccess() {
            // Core specification: O(1) index access
            for (int i = 0; i < 1000; i++) {
                list.add("element" + i);
            }

            // This should be fast (O(1)) regardless of list size
            String element = list.get(500);
            assertEquals("element500", element);
        }

        @Test
        @DisplayName("should support manual sorting")
        void shouldSupportManualSorting() {
            // Core specification: manual sorting capability
            list.add("zebra");
            list.add("apple");
            list.add("banana");

            list.sort();

            assertEquals("apple", list.get(0));
            assertEquals("banana", list.get(1));
            assertEquals("zebra", list.get(2));
        }

        @Test
        @DisplayName("should be suitable for UI list models")
        void shouldBeSuitableForUIListModels() {
            // Core specification: UI list model suitability
            list.add("item1");
            list.add("item2");
            list.add("item3");

            // UI models need: size, get(index), iteration
            assertEquals(3, list.size());
            assertEquals("item1", list.get(0));

            Iterator<String> iterator = list.iterator();
            assertTrue(iterator.hasNext());
            assertEquals("item1", iterator.next());
        }
    }

    @Nested
    @DisplayName("Uniqueness Guarantees")
    class UniquenessGuarantees {

        @Test
        @DisplayName("should reject duplicates via add()")
        void shouldRejectDuplicatesViaAdd() {
            assertTrue(list.add("test"));
            assertFalse(list.add("test")); // Duplicate rejected
            assertEquals(1, list.size());
        }

        @Test
        @DisplayName("should reject duplicates via addAll()")
        void shouldRejectDuplicatesViaAddAll() {
            list.add("existing");
            Collection<String> newItems = Arrays.asList("existing", "new");

            assertTrue(list.addAll(newItems)); // Some items added
            assertEquals(2, list.size()); // Only "new" was added
            assertTrue(list.contains("existing"));
            assertTrue(list.contains("new"));
        }

        @Test
        @DisplayName("should allow re-adding after removal")
        void shouldAllowReAddingAfterRemoval() {
            list.add("test");
            list.remove("test");
            assertTrue(list.add("test")); // Should work after removal
        }
    }

    @Nested
    @DisplayName("Index-Based Access")
    class IndexBasedAccess {

        @Test
        @DisplayName("should provide correct indices after operations")
        void shouldProvideCorrectIndicesAfterOperations() {
            list.add("first");
            list.add("second");
            list.add("third");

            assertEquals(0, list.indexOf("first"));
            assertEquals(1, list.indexOf("second"));
            assertEquals(2, list.indexOf("third"));

            list.remove("second");
            assertEquals(0, list.indexOf("first"));
            assertEquals(1, list.indexOf("third"));
        }

        @Test
        @DisplayName("should maintain indices during iteration")
        void shouldMaintainIndicesDuringIteration() {
            list.add("a");
            list.add("b");
            list.add("c");

            int index = 0;
            for (String item : list) {
                assertEquals(item, list.get(index));
                index++;
            }
        }
    }

    @Nested
    @DisplayName("Data Structure Consistency")
    class DataStructureConsistency {

        @Test
        @DisplayName("should maintain set and list in sync")
        void shouldMaintainSetAndListInSync() {
            // This tests the core invariant: set and list must be in sync
            list.add("test");

            // Both data structures should contain the same elements
            assertTrue(list.contains("test"));
            assertEquals(1, list.size());

            list.remove("test");
            assertFalse(list.contains("test"));
            assertEquals(0, list.size());
        }

        @Test
        @DisplayName("should handle bulk operations correctly")
        void shouldHandleBulkOperationsCorrectly() {
            list.add("a");
            list.add("b");
            list.add("c");

            Collection<String> toRemove = Arrays.asList("a", "c");
            list.removeAll(toRemove);

            assertEquals(1, list.size());
            assertTrue(list.contains("b"));
            assertFalse(list.contains("a"));
            assertFalse(list.contains("c"));
        }
    }

    @Nested
    @DisplayName("Comparable Requirement")
    class ComparableRequirement {

        @Test
        @DisplayName("should work with Comparable elements")
        void shouldWorkWithComparableElements() {
            // Specification requires E extends Comparable<? super E>
            IndexedUniqueList<Integer> intList = new IndexedUniqueList<>();
            intList.add(3);
            intList.add(1);
            intList.add(2);

            intList.sort();
            assertEquals(1, intList.get(0));
            assertEquals(2, intList.get(1));
            assertEquals(3, intList.get(2));
        }

        @Test
        @DisplayName("should handle custom Comparable objects")
        void shouldHandleCustomComparableObjects() {
            IndexedUniqueList<TestComparable> comparableList = new IndexedUniqueList<>();
            TestComparable a = new TestComparable("a", 1);
            TestComparable b = new TestComparable("b", 2);
            TestComparable c = new TestComparable("c", 0);

            comparableList.add(a);
            comparableList.add(b);
            comparableList.add(c);

            comparableList.sort();
            assertEquals(c, comparableList.get(0)); // lowest value
            assertEquals(a, comparableList.get(1));
            assertEquals(b, comparableList.get(2)); // highest value
        }

        private static class TestComparable implements Comparable<TestComparable> {
            private final String name;
            private final int value;

            TestComparable(String name, int value) {
                this.name = name;
                this.value = value;
            }

            @Override
            public int compareTo(TestComparable other) {
                return Integer.compare(this.value, other.value);
            }

            @Override
            public boolean equals(Object obj) {
                if (this == obj) return true;
                if (obj == null || getClass() != obj.getClass()) return false;
                TestComparable that = (TestComparable) obj;
                return value == that.value && name.equals(that.name);
            }

            @Override
            public int hashCode() {
                return name.hashCode() * 31 + value;
            }
        }
    }

    @Nested
    @DisplayName("Error Conditions")
    class ErrorConditions {

        @Test
        @DisplayName("should reject invalid indices")
        void shouldRejectInvalidIndices() {
            assertThrows(IllegalArgumentException.class, () -> list.get(-1));
            assertThrows(IllegalArgumentException.class, () -> list.get(0));
        }

        @Test
        @DisplayName("should reject unsupported operations")
        void shouldRejectUnsupportedOperations() {
            // These operations would break the uniqueness invariant
            list.add("test");
            assertThrows(UnsupportedOperationException.class, () -> list.set(0, "new"));
            assertThrows(UnsupportedOperationException.class, () -> list.add(0, "new"));
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("should handle empty collection")
        void shouldHandleEmptyCollection() {
            assertTrue(list.isEmpty());
            assertEquals(0, list.size());
            assertFalse(list.contains("anything"));
        }

        @Test
        @DisplayName("should handle single element")
        void shouldHandleSingleElement() {
            list.add("test");
            assertEquals(1, list.size());
            assertEquals("test", list.get(0));
            assertTrue(list.contains("test"));
        }

        @Test
        @DisplayName("should handle large collections")
        void shouldHandleLargeCollections() {
            // Test that the O(1) operations scale well
            for (int i = 0; i < 10000; i++) {
                list.add("element" + i);
            }
            assertEquals(10000, list.size());

            // Test that index access is still fast
            String element = list.get(5000);
            assertEquals("element5000", element);
        }
    }

    @Nested
    @DisplayName("Specification Questions")
    class SpecificationQuestions {

        @Test
        @DisplayName("should question the Comparable requirement")
        void shouldQuestionTheComparableRequirement() {
            // QUESTION: Why does IndexedUniqueList require Comparable?
            // The only operation that uses Comparable is sort(), but:
            // 1. sort() is optional - not all use cases need sorting
            // 2. This prevents using IndexedUniqueList with non-Comparable objects
            // 3. It's overly restrictive for a collection that's primarily about uniqueness +
            // indexing

            // This test demonstrates the limitation:
            // IndexedUniqueList<Object> objectList = new IndexedUniqueList<>(); // Won't compile
            // Because Object doesn't implement Comparable

            // SUGGESTION: Consider making Comparable optional and only requiring it for sort()
            // Or provide a separate SortedIndexedUniqueList class
        }

        @Test
        @DisplayName("should question the List interface implementation")
        void shouldQuestionTheListInterfaceImplementation() {
            // QUESTION: Is implementing List<E> the right choice?
            // IndexedUniqueList violates several List contracts:

            // 1. List allows duplicates, IndexedUniqueList doesn't
            // 2. List allows set(index, element), IndexedUniqueList doesn't
            // 3. List allows add(index, element), IndexedUniqueList doesn't

            // This creates confusion about what operations are supported
            // SUGGESTION: Consider implementing Collection<E> + custom index methods
            // Or create a more specific interface like UniqueIndexedCollection<E>
        }

        @Test
        @DisplayName("should question the dual data structure approach")
        void shouldQuestionTheDualDataStructureApproach() {
            // QUESTION: Is maintaining HashSet + ArrayList the best approach?

            // PROS:
            // - O(1) duplicate checking (HashSet)
            // - O(1) index access (ArrayList)
            // - O(1) contains() (HashSet)

            // CONS:
            // - Memory overhead (2x storage)
            // - Complexity in keeping structures in sync
            // - Potential for bugs if sync is broken

            // ALTERNATIVES:
            // 1. Use LinkedHashSet (maintains insertion order, O(1) contains)
            //    But no O(1) index access
            // 2. Use ArrayList with manual duplicate checking
            //    But O(n) contains and duplicate checking

            // The current approach is a reasonable trade-off for the stated use case
            // (UI list models that need both uniqueness and indexed access)
        }

        @Test
        @DisplayName("should question the null handling")
        void shouldQuestionTheNullHandling() {
            // QUESTION: Should IndexedUniqueList allow null elements?

            // Current behavior: allows null (inherited from HashSet)
            assertTrue(list.add(null));
            assertTrue(list.contains(null));
            assertNull(list.get(0));

            // ISSUES:
            // 1. null elements can cause issues in UI list models
            // 2. null elements can cause issues with sorting
            // 3. Inconsistent with typical UI requirements

            // SUGGESTION: Consider explicitly rejecting null elements
            // Or document that null elements are not recommended for UI use
        }

        @Test
        @DisplayName("should question the sorting behavior")
        void shouldQuestionTheSortingBehavior() {
            // QUESTION: Is the current sorting approach optimal?

            list.add("zebra");
            list.add("apple");
            list.add("banana");

            // Current approach: sorts in-place, modifies insertion order
            list.sort();

            // ISSUES:
            // 1. Destroys the original insertion order
            // 2. No way to "unsort" back to insertion order
            // 3. No way to sort by different criteria

            // SUGGESTIONS:
            // 1. Provide a sorted view without modifying the original
            // 2. Add a "sortBy" method that takes a Comparator
            // 3. Add a "resetOrder" method to restore insertion order
        }
    }
}
