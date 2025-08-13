package components.wordpool;

import java.awt.event.KeyEvent;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;

/**
 * Simple <code>JScrollPane</code> container for the <code>WordpoolList</code>. Nearly the same as a
 * default <code>JScrollPane</code>.
 */
public class WordpoolScrollPane extends JScrollPane {

    private static WordpoolList list;

    /**
     * Creates a new <code>WordpoolScrollPane</code>, initializing the view to <code>WordpoolList
     * </code> and key bindings.
     */
    @SuppressWarnings("StaticAssignmentInConstructor")
    protected WordpoolScrollPane() {
        setOpaque(false);
        list = WordpoolList.getInstance();
        getViewport().setView(list);

        // overrides JScrollPane key bindings for the benefit of SeekAction's key bindings
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0, false), "none");
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0, false), "none");
    }
}
