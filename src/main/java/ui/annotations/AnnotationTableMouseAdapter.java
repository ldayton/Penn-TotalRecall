package ui.annotations;

// import actions.JumpToAnnotationAction;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import lombok.NonNull;

// import state.AudioState;

/** Mouse adapter for the <code>AnnotationTable</code>. */
@Singleton
public class AnnotationTableMouseAdapter extends MouseAdapter {

    // private final JumpToAnnotationAction jumpToAnnotationAction;
    private final AnnotationTablePopupMenuFactory popupMenuFactory;

    // private final AudioState audioState;

    @Inject
    public AnnotationTableMouseAdapter(
            // JumpToAnnotationAction jumpToAnnotationAction,
            AnnotationTablePopupMenuFactory popupMenuFactory /* , AudioState audioState */) {
        // this.jumpToAnnotationAction = jumpToAnnotationAction;
        this.popupMenuFactory = popupMenuFactory;
        // this.audioState = audioState;
    }

    @Override
    public void mouseClicked(@NonNull MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 2) {
            // if (!audioState.isPlaying()) {
            // we are manually generating the event, so we must ourselves check the conditions
            // jumpToAnnotationAction.actionPerformed(
            //         new ActionEvent(
            //                 AnnotationTable.getInstance(),
            //                 ActionEvent.ACTION_PERFORMED,
            //                 null,
            //                 System.currentTimeMillis(),
            //                 0));
            // }
        }
    }

    @Override
    public void mousePressed(@NonNull MouseEvent e) {
        evaluatePopup(e);
    }

    @Override
    public void mouseReleased(@NonNull MouseEvent e) {
        evaluatePopup(e);
    }

    public void evaluatePopup(@NonNull MouseEvent e) {
        if (e.isPopupTrigger()) {
            AnnotationTable table = (AnnotationTable) e.getSource();
            int rIndex = table.rowAtPoint(e.getPoint());
            int cIndex = table.columnAtPoint(e.getPoint());
            if (rIndex < 0 || cIndex < 0) {
                return; // event not on an entry
            }
            var first =
                    AnnotationTableCellRenderer.noDecimalsFormat.format(
                            table.getValueAt(rIndex, AnnotationColumn.TIME.index()));
            var second = table.getValueAt(rIndex, AnnotationColumn.WORD.index()).toString();
            var third = table.getValueAt(rIndex, AnnotationColumn.WORD_NUM.index()).toString();
            var rowRepr = first + " " + second + " " + third;
            var annToDelete = table.getModel().toArray()[rIndex];
            var pop = popupMenuFactory.createPopupMenu(annToDelete, rIndex, table, rowRepr);
            pop.show(e.getComponent(), e.getX(), e.getY());
        }
    }
}
