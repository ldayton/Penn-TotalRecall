package ui.annotations;

import core.annotations.Annotation;
import core.dispatch.EventDispatchBus;
import core.dispatch.Subscribe;
import core.events.UiUpdateEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.Dimension;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Objects;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import lombok.NonNull;
import ui.layout.UiStyles;

/** A custom interface component for displaying committed annotations to the user. */
@Singleton
public class AnnotationDisplay extends JScrollPane {

    private static final String title = "Annotations";
    private static final int INNER_PADDING_PX = 8;
    private static final Dimension PREFERRED_SIZE = new Dimension(300, 180);
    private final AnnotationTable annotationTable;

    /**
     * Creates a new instance of the component, initializing internal components, key bindings,
     * listeners, and various aspects of appearance.
     */
    @Inject
    public AnnotationDisplay(
            @NonNull AnnotationTable annotationTable, @NonNull EventDispatchBus eventBus) {
        this.annotationTable = annotationTable;
        getViewport().setView(annotationTable);
        setPreferredSize(PREFERRED_SIZE);
        // Allow vertical growth; keep initial width
        setMaximumSize(new Dimension(PREFERRED_SIZE.width, Integer.MAX_VALUE));

        // Subtle rounded border with inner padding
        setBorder(UiStyles.roundedBox(INNER_PADDING_PX));

        // since AnnotationDisplay is a clickable area, we must write focus handling code for the
        // event it is clicked on
        // passes focus to the table if it is focusable (not empty), otherwise giving focus to the
        // frame
        addMouseListener(
                new MouseAdapter() {
                    @Override
                    public void mousePressed(@NonNull MouseEvent e) {
                        if (annotationTable.isFocusable()) {
                            annotationTable.requestFocusInWindow();
                        } else {
                            getParent().requestFocusInWindow();
                        }
                    }
                });

        // overrides JScrollPane key bindings for the benefit of SeekAction's key bindings
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0, false), "none");
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0, false), "none");

        // Subscribe to UI update events
        eventBus.subscribe(this);
    }

    public Annotation[] getAnnotationsInOrder() {
        return annotationTable.getModel().toArray();
    }

    public void addAnnotation(@NonNull Annotation ann) {
        Objects.requireNonNull(ann, "annotation cannot be null");
        annotationTable.getModel().addElement(ann);
    }

    public void addAnnotations(@NonNull Iterable<Annotation> anns) {
        Objects.requireNonNull(anns, "annotations cannot be null");
        annotationTable.getModel().addElements(anns);
    }

    public void removeAnnotation(int rowIndex) {
        annotationTable.getModel().removeElementAt(rowIndex);
    }

    public void removeAllAnnotations() {
        annotationTable.getModel().removeAllElements();
    }

    public int getNumAnnotations() {
        return annotationTable.getModel().size();
    }

    @Subscribe
    public void handleUIUpdateRequestedEvent(@NonNull UiUpdateEvent event) {
        if (event.component() == UiUpdateEvent.Component.ANNOTATION_DISPLAY) {
            repaint();
        }
    }
}
