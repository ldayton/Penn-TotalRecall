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
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;

/** A custom interface component for displaying committed annotations to the user. */
@Singleton
public class AnnotationDisplay extends JScrollPane {

    private static final String title = "Annotations";
    private static final Dimension PREFERRED_SIZE = new Dimension(300, Integer.MAX_VALUE);
    private final AnnotationTable annotationTable;

    /**
     * Creates a new instance of the component, initializing internal components, key bindings,
     * listeners, and various aspects of appearance.
     */
    @Inject
    public AnnotationDisplay(AnnotationTable annotationTable, EventDispatchBus eventBus) {
        this.annotationTable = annotationTable;
        getViewport().setView(annotationTable);
        setPreferredSize(PREFERRED_SIZE);
        setMaximumSize(PREFERRED_SIZE);

        setBorder(BorderFactory.createTitledBorder(title));

        // since AnnotationDisplay is a clickable area, we must write focus handling code for the
        // event it is clicked on
        // passes focus to the table if it is focusable (not empty), otherwise giving focus to the
        // frame
        addMouseListener(
                new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent e) {
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

    public void addAnnotation(Annotation ann) {
        if (ann == null) {
            throw new IllegalArgumentException("annotation/s cannot be null");
        }
        annotationTable.getModel().addElement(ann);
    }

    public void addAnnotations(Iterable<Annotation> anns) {
        if (anns == null) {
            throw new IllegalArgumentException("annotations cannot be null");
        }
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
    public void handleUIUpdateRequestedEvent(UiUpdateEvent event) {
        if (event.component() == UiUpdateEvent.Component.ANNOTATION_DISPLAY) {
            repaint();
        }
    }
}
