package ui.annotations;

// import actions.DeleteAnnotationAction;
import core.annotations.Annotation;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Factory for creating AnnotationTablePopupMenu instances. This allows multiple popup menus to be
 * created without singleton conflicts.
 */
@Singleton
public class AnnotationTablePopupMenuFactory {

    // private final DeleteAnnotationAction deleteAnnotationAction;

    @Inject
    public AnnotationTablePopupMenuFactory(/* DeleteAnnotationAction deleteAnnotationAction */ ) {
        // this.deleteAnnotationAction = deleteAnnotationAction;
    }

    /**
     * Creates a new AnnotationTablePopupMenu instance configured for the given annotation.
     *
     * @param annToDelete The annotation to delete
     * @param rowIndex The row index of the annotation
     * @param table The annotation table
     * @param rowRepr The string representation of the row
     * @return A configured AnnotationTablePopupMenu instance
     */
    public AnnotationTablePopupMenu createPopupMenu(
            Annotation annToDelete, int rowIndex, AnnotationTable table, String rowRepr) {
        AnnotationTablePopupMenu popupMenu =
                new AnnotationTablePopupMenu(/* deleteAnnotationAction */ );
        popupMenu.configureForAnnotation(annToDelete, rowIndex, table, rowRepr);
        return popupMenu;
    }
}
