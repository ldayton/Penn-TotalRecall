package ui.annotations;

import actions.JumpToAnnotationAction;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import state.AudioState;

/** Mouse adapter for the <code>AnnotationTable</code>. */
@Singleton
public class AnnotationTableMouseAdapter extends MouseAdapter {

    private final JumpToAnnotationAction jumpToAnnotationAction;
    private final AnnotationTablePopupMenuFactory popupMenuFactory;
    private final AudioState audioState;

    @Inject
    public AnnotationTableMouseAdapter(
            JumpToAnnotationAction jumpToAnnotationAction,
            AnnotationTablePopupMenuFactory popupMenuFactory,
            AudioState audioState) {
        this.jumpToAnnotationAction = jumpToAnnotationAction;
        this.popupMenuFactory = popupMenuFactory;
        this.audioState = audioState;
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 2) {
            if (!audioState.isPlaying()) {
                // we are manually generating the event, so we must ourselves check the conditions
                jumpToAnnotationAction.actionPerformed(
                        new ActionEvent(
                                AnnotationTable.getInstance(),
                                ActionEvent.ACTION_PERFORMED,
                                null,
                                System.currentTimeMillis(),
                                0));
            }
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
        evaluatePopup(e);
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        evaluatePopup(e);
    }

    public void evaluatePopup(MouseEvent e) {
        if (e.isPopupTrigger()) {
            AnnotationTable table = (AnnotationTable) e.getSource();
            int rIndex = table.rowAtPoint(e.getPoint());
            int cIndex = table.columnAtPoint(e.getPoint());
            if (rIndex < 0 || cIndex < 0) {
                return; // event not on an entry
            }
            String first =
                    AnnotationTableCellRenderer.noDecimalsFormat.format(
                            table.getValueAt(rIndex, 0));
            String second = table.getValueAt(rIndex, 1).toString();
            String third = table.getValueAt(rIndex, 2).toString();
            String rowRepr = first + " " + second + " " + third;
            Annotation annToDelete = AnnotationDisplay.getAnnotationsInOrder()[rIndex];
            AnnotationTablePopupMenu pop =
                    popupMenuFactory.createPopupMenu(annToDelete, rIndex, table, rowRepr);
            pop.show(e.getComponent(), e.getX(), e.getY());
        }
    }
}
