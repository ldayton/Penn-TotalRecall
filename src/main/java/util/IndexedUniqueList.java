package util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * A collection that maintains uniqueness, provides index-based access, and supports manual sorting.
 *
 * <p>This class combines the benefits of a HashSet (O(1) duplicate checking) with an ArrayList
 * (O(1) index access) to provide a collection suitable for UI list models that need both uniqueness
 * guarantees and indexed access to sorted elements.
 *
 * <p>The collection maintains two internal data structures in sync: a HashSet for fast duplicate
 * detection and an ArrayList for indexed access and iteration.
 *
 * @param <E> the type of elements in this collection, must be comparable
 */
public class IndexedUniqueList<E extends Comparable<? super E>> implements List<E> {

    private final HashSet<E> set;
    private final ArrayList<E> list;

    public IndexedUniqueList() {
        set = new HashSet<E>();
        list = new ArrayList<E>();
    }

    @Override
    public boolean add(E e) {
        if (set.add(e) == false) {
            return false;
        } else {
            list.add(e);
            return true;
        }
    }

    @Override
    public E remove(int index) {
        if (index < 0 || index > list.size() - 1) {
            throw new IllegalArgumentException("index not in range: " + index);
        }
        if (set.remove(list.get(index))) {
            return list.remove(index);
        } else {
            throw new IllegalStateException("list and set desynched");
        }
    }

    @Override
    public boolean remove(Object o) {
        if (set.remove(o)) {
            return list.remove(o);
        }
        return false;
    }

    @Override
    public boolean removeAll(Collection<?> toRemove) {
        boolean modified = list.removeAll(toRemove);
        if (modified) {
            set.removeAll(toRemove);
        }
        return modified;
    }

    @Override
    public E get(int index) {
        if (index < 0 || index > list.size() - 1) {
            throw new IllegalArgumentException("index not in range: " + index);
        }
        return list.get(index);
    }

    @Override
    public int size() {
        return list.size();
    }

    /** Sorts the elements in this collection using their natural ordering. */
    public void sort() {
        Collections.sort(list);
    }

    @Override
    public boolean contains(Object e) {
        return set.contains(e);
    }

    @Override
    public void clear() {
        set.clear();
        list.clear();
    }

    @Override
    public Iterator<E> iterator() {
        return list.iterator();
    }

    @Override
    public Object[] toArray() {
        return list.toArray();
    }

    // Required List<E> interface methods with default implementations
    @Override
    public boolean isEmpty() {
        return list.isEmpty();
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return set.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        boolean modified = false;
        for (E e : c) {
            if (add(e)) {
                modified = true;
            }
        }
        return modified;
    }

    @Override
    public boolean addAll(int index, Collection<? extends E> c) {
        throw new UnsupportedOperationException(
                "IndexedUniqueList does not support indexed addAll");
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        boolean modified = list.retainAll(c);
        if (modified) {
            set.retainAll(c);
        }
        return modified;
    }

    @Override
    public E set(int index, E element) {
        throw new UnsupportedOperationException("IndexedUniqueList does not support set operation");
    }

    @Override
    public void add(int index, E element) {
        throw new UnsupportedOperationException("IndexedUniqueList does not support indexed add");
    }

    @Override
    public int indexOf(Object o) {
        return list.indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        return list.lastIndexOf(o);
    }

    @Override
    public ListIterator<E> listIterator() {
        return list.listIterator();
    }

    @Override
    public ListIterator<E> listIterator(int index) {
        return list.listIterator(index);
    }

    @Override
    public List<E> subList(int fromIndex, int toIndex) {
        throw new UnsupportedOperationException("IndexedUniqueList does not support subList");
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return list.toArray(a);
    }
}
