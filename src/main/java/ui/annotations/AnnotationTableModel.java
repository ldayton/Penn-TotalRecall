package ui.annotations;

import core.annotations.Annotation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;
import lombok.NonNull;

/** Custom <code>TableModel</code> for storing annotations of the open audio file. */
public class AnnotationTableModel implements TableModel {

    private final Set<TableModelListener> listeners;

    private final List<Annotation> sortedAnns;

    private static final String colErr = "column index out of range";
    private static final String rowErr = "row index out of range";
    private static final String stateErr = "inconsistency in internal column handling";

    protected AnnotationTableModel() {
        listeners = new HashSet<>();
        sortedAnns = new ArrayList<>();
    }

    @Override
    public int getColumnCount() {
        return AnnotationColumn.values().length;
    }

    @Override
    public int getRowCount() {
        return sortedAnns.size();
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return AnnotationColumn.fromIndex(columnIndex).columnClass();
    }

    @Override
    public boolean isCellEditable(int row, int col) {
        return false;
    }

    @Override
    public void addTableModelListener(@NonNull TableModelListener l) {
        listeners.add(l);
    }

    @Override
    public void removeTableModelListener(@NonNull TableModelListener l) {
        listeners.remove(l);
    }

    @Override
    public String getColumnName(int columnIndex) {
        return AnnotationColumn.fromIndex(columnIndex).header();
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if (rowIndex < 0 || rowIndex > sortedAnns.size()) {
            throw new IllegalArgumentException(rowErr);
        }
        if (columnIndex < 0 || columnIndex >= getColumnCount()) {
            throw new IllegalArgumentException(colErr);
        }
        var ann = sortedAnns.get(rowIndex);
        return AnnotationColumn.fromIndex(columnIndex).value(ann);
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
        return sortedAnns.toArray(Annotation[]::new);
    }

    // adding duplicates is prevented by annotation-over deleting first annotation, performed in
    // annotateaction
    protected void addElement(@NonNull Annotation ann) {
        Objects.requireNonNull(ann, "annotation cannot be null");
        sortedAnns.add(ann);
        // then remove batch adding option below
        sortedAnns.sort(null);
        listeners.forEach(tml -> tml.tableChanged(new TableModelEvent(this)));
    }

    // duplicate adds are possible with this method
    protected void addElements(@NonNull Iterable<Annotation> batch) {
        Objects.requireNonNull(batch, "batch cannot be null");
        for (var el : batch) {
            sortedAnns.add(Objects.requireNonNull(el, "annotation cannot be null"));
        }
        sortedAnns.sort(null);
        listeners.forEach(tml -> tml.tableChanged(new TableModelEvent(this)));
    }

    protected void removeElementAt(int index) {
        if (index < 0 || index > sortedAnns.size()) {
            throw new IllegalArgumentException(rowErr);
        }
        sortedAnns.remove(index);
        listeners.forEach(
                tml ->
                        tml.tableChanged(
                                new TableModelEvent(
                                        this,
                                        Math.min(index, sortedAnns.size()),
                                        sortedAnns.size())));
    }

    protected void removeAllElements() {
        sortedAnns.clear();
    }

    public int size() {
        return sortedAnns.size();
    }
}
