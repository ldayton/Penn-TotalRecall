package components.wordpool;

import java.awt.Component;
import java.awt.Font;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;

/**
 * A <code>DefaultListCellRenderer</code> whose appearance is determined by whether the {@link
 * components.wordpool.WordpoolList} it is displaying is done being annotated or not. <code>
 * WordpoolWords</code> from the audio file's lst file are displayed using the program's bold <code>
 * Font</code>. <code>WordpoolWords</code> from the general wordpool list are displayed using the
 * program's plain <code>Font</code>.
 */
public class WordpoolListCellRenderer extends DefaultListCellRenderer {

    private final Font boldFont;

    public WordpoolListCellRenderer() {
        boldFont = getFont().deriveFont(Font.BOLD);
    }

    /** {@inheritDoc} */
    @Override
    public Component getListCellRendererComponent(
            JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        WordpoolWord word = (value instanceof WordpoolWord) ? (WordpoolWord) value : null;
        if (word != null && word.isLst()) {
            setFont(boldFont);
        }
        return this;
    }
}
