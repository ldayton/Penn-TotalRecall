package ui.annotations;

import java.awt.Component;
import java.text.DecimalFormat;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import lombok.NonNull;

/** The MVC "view" of a cell of the <code>AnnotationTable</code>. */
public class AnnotationTableCellRenderer extends DefaultTableCellRenderer {

    protected static final DecimalFormat noDecimalsFormat = new DecimalFormat("0");

    @Override
    public Component getTableCellRendererComponent(
            @NonNull JTable table,
            Object value,
            boolean isSelected,
            boolean hasFocus,
            int row,
            int column) {

        var renderedLabel =
                (JLabel)
                        super.getTableCellRendererComponent(
                                table, value, isSelected, hasFocus, row, column);
        renderedLabel.setHorizontalAlignment(SwingConstants.LEADING);
        return renderedLabel;
    }

    @Override
    protected void setValue(Object value) {
        if (value == null) {
            setText("");
            return;
        }
        if (value instanceof Double d) {
            setText(noDecimalsFormat.format(d));
            return;
        }
        setText(value.toString());
    }
}
