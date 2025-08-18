package components.annotations;

import audio.AudioPlayer;
import behaviors.singleact.JumpToAnnotationAction;
import control.CurAudio;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** Mouse adapter for the <code>AnnotationTable</code>. */
public class AnnotationTableMouseAdapter extends MouseAdapter {

    private final AnnotationTable table;

    protected AnnotationTableMouseAdapter(AnnotationTable table) {
        this.table = table;
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 2) {
            JumpToAnnotationAction jumpAct = new JumpToAnnotationAction();
            if ((CurAudio.getPlayer().getStatus() == AudioPlayer.Status.PLAYING) == false) {
                // we are manually generating the event, so we must ourselves check the conditions
                jumpAct.actionPerformed(
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
                    new AnnotationTablePopupMenu(annToDelete, rIndex, table, rowRepr);
            pop.show(e.getComponent(), e.getX(), e.getY());
        }
    }
}
