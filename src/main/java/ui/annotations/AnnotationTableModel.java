package ui.annotations;

import core.annotations.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;

/** Custom <code>TableModel</code> for storing annotations of the open audio file. */
public class AnnotationTableModel implements TableModel {

    private HashSet<TableModelListener> listeners;

    private ArrayList<Annotation> sortedAnns;

    // editing the table layout (e.g., adding a new column, switching the order of two columns)
    // involves more than changing the next three lines
    // some of the methods below make assumptions about the number of columns and the Annotation
    // methods they hook up to
    // doing this in a perfectly programmed world would involve storing an array of Method objects
    private static final int columnCount = 3;
    private static final Class<?>[] columnClasses =
            new Class<?>[] {Double.class, String.class, Integer.class};
    private static final String[] columnNames = new String[] {"Time (ms)", "Word", "Word #"};

    private static final String colErr = "column index out of range";
    private static final String rowErr = "row index out of range";
    private static final String stateErr = "inconsistency in internal column handling";

    protected AnnotationTableModel() {
        if (columnCount != columnClasses.length || columnCount != columnNames.length) {
            throw new IllegalStateException(stateErr);
        }
        listeners = new HashSet<TableModelListener>();
        sortedAnns = new ArrayList<Annotation>();
    }

    @Override
    public int getColumnCount() {
        return columnCount;
    }

    @Override
    public int getRowCount() {
        return sortedAnns.size();
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        if (columnIndex > columnClasses.length || columnIndex < 0) {
            throw new IllegalArgumentException(colErr);
        }
        return columnClasses[columnIndex];
    }

    @Override
    public boolean isCellEditable(int row, int col) {
        return false;
    }

    @Override
    public void addTableModelListener(TableModelListener l) {
        listeners.add(l);
    }

    @Override
    public void removeTableModelListener(TableModelListener l) {
        listeners.remove(l);
    }

    @Override
    public String getColumnName(int columnIndex) {
        if (columnIndex > columnClasses.length || columnIndex < 0) {
            throw new IllegalArgumentException(colErr);
        }
        return columnNames[columnIndex];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if (rowIndex < 0 || rowIndex > sortedAnns.size()) {
            throw new IllegalArgumentException(rowErr);
        }
        if (columnIndex > columnCount - 1) {
            throw new IllegalArgumentException(colErr);
        }
        Annotation ann = sortedAnns.get(rowIndex);
        if (columnIndex == 0) {
            return ann.time();
        }
        if (columnIndex == 1) {
            return ann.text();
        }
        if (columnIndex == 2) {
            return ann.wordNum();
        }
        throw new IllegalStateException(stateErr);
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        throw new UnsupportedOperationException(
                "setting table values not supported, use add/remove annotation methods");
    }

    protected Annotation getAnnotationAt(int rowIndex) {
        if (rowIndex < 0 || rowIndex > sortedAnns.size()) {
            throw new IllegalArgumentException(rowErr);
        }
        return sortedAnns.get(rowIndex);
    }

    protected Annotation[] toArray() {
        return sortedAnns.toArray(new Annotation[sortedAnns.size()]);
    }

    // adding duplicates is prevented by annotation-over deleting first annotation, performed in
    // annotateaction
    protected void addElement(Annotation ann) {
        sortedAnns.add(ann);
        // then remove batch adding option below
        Collections.sort(sortedAnns);
        for (TableModelListener tml : listeners) {
            tml.tableChanged(new TableModelEvent(this));
        }
    }

    // duplicate adds are possible with this method
    protected void addElements(Iterable<Annotation> batch) {
        for (Annotation el : batch) {
            sortedAnns.add(el);
        }
        Collections.sort(sortedAnns);
        for (TableModelListener tml : listeners) {
            tml.tableChanged(new TableModelEvent(this));
        }
    }

    protected void removeElementAt(int index) {
        if (index < 0 || index > sortedAnns.size()) {
            throw new IllegalArgumentException(rowErr);
        }
        sortedAnns.remove(index);
        for (TableModelListener tml : listeners) {
            tml.tableChanged(
                    new TableModelEvent(
                            this, Math.min(index, sortedAnns.size()), sortedAnns.size()));
        }
    }

    protected void removeAllElements() {
        sortedAnns.clear();
    }

    public int size() {
        return sortedAnns.size();
    }
}
