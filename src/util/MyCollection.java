package util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;

public class MyCollection<E extends Comparable<? super E>> implements Iterable<E> {

	private HashSet<E> set;
	private ArrayList<E> list;
	
	public MyCollection() {
		set = new HashSet<E>();
		list = new ArrayList<E>();
	}
	
	public boolean add(E e) {
		if(set.add(e) == false) {
			return false;
		}
		else {
			list.add(e);
			return true;
		}
	}
	
	public E linearRemoveAt(int index) {
		if(index < 0 || index > list.size() - 1) {
			throw new IllegalArgumentException("index not in range: " + index);
		}
		if(set.remove(list.get(index))) {
			return list.remove(index);
		}
		else {
			throw new IllegalStateException("list and set desynched");
		}
	}

	public void linearRemove(Collection<E> toRemove) {
		list.removeAll(toRemove);
		set.removeAll(toRemove);
	}
	
	public E get(int index) {
		if(index < 0 || index > list.size() - 1) {
			throw new IllegalArgumentException("index not in range: " + index);
		}
		return list.get(index);
	}
	
	public int size() {
		return list.size();
	}
	
	public void sort() {
		Collections.sort(list);
	}

	public boolean contains(E e) {
		return set.contains(e);
	}

	public void clear() {
		set.clear();
		list.clear();
	}

	public Iterator<E> iterator() {
		return list.iterator();
	}

	public Object[] toArray() {
		return list.toArray();
	}
}
