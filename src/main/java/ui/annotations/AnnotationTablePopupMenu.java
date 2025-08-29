package ui.annotations;

import actions.DeleteAnnotationAction;
import jakarta.inject.Inject;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

/** Popup menu launched by right clicking on annotations. */
public class AnnotationTablePopupMenu extends JPopupMenu {

    private final DeleteAnnotationAction deleteAnnotationAction;

    @Inject
    public AnnotationTablePopupMenu(DeleteAnnotationAction deleteAnnotationAction) {
        this.deleteAnnotationAction = deleteAnnotationAction;
    }

    public void configureForAnnotation(
            Annotation annToDelete, int rowIndex, AnnotationTable table, String rowRepr) {
        removeAll(); // Clear existing items

        JMenuItem fakeTitle = new JMenuItem(rowRepr + "...");
        fakeTitle.setEnabled(false);

        // Configure the injected action for this specific row
        deleteAnnotationAction.setRowIndex(rowIndex);
        JMenuItem del = new JMenuItem(deleteAnnotationAction);

        add(fakeTitle);
        addSeparator();
        add(del);
    }
}
