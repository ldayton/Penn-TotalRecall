package components.annotations;

import app.di.GuiceBootstrap;
import events.EventDispatchBus;
import events.Subscribe;
import events.UIUpdateRequestedEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import ui.UiConstants;
import ui.UiShapes;

/** A custom interface component for displaying committed annotations to the user. */
@Singleton
public class AnnotationDisplay extends JScrollPane {

    private static final String title = "Annotations";

    private static AnnotationDisplay instance;
    private static AnnotationTable table;
    private final AnnotationTable annotationTable;
    private final EventDispatchBus eventBus;

    /**
     * Creates a new instance of the component, initializing internal components, key bindings,
     * listeners, and various aspects of appearance.
     */
    @SuppressWarnings("StaticAssignmentInConstructor")
    @Inject
    public AnnotationDisplay(AnnotationTable annotationTable, EventDispatchBus eventBus) {
        this.annotationTable = annotationTable;
        this.eventBus = eventBus;
        table = annotationTable;
        getViewport().setView(table);
        setPreferredSize(UiConstants.annotationDisplayDimension);
        setMaximumSize(UiConstants.annotationDisplayDimension);

        setBorder(UiShapes.createMyUnfocusedTitledBorder(title));

        // since AnnotationDisplay is a clickable area, we must write focus handling code for the
        // event it is clicked on
        // passes focus to the table if it is focusable (not empty), otherwise giving focus to the
        // frame
        addMouseListener(
                new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent e) {
                        if (table.isFocusable()) {
                            table.requestFocusInWindow();
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

        // Set the singleton instance after full initialization
        instance = this;

        // Subscribe to UI update events
        eventBus.subscribe(this);
    }

    public static Annotation[] getAnnotationsInOrder() {
        var instance = GuiceBootstrap.getInjectedInstance(AnnotationDisplay.class);
        if (instance == null) {
            throw new IllegalStateException("AnnotationDisplay not available via DI");
        }
        return instance.annotationTable.getModel().toArray();
    }

    public static void addAnnotation(Annotation ann) {
        if (ann == null) {
            throw new IllegalArgumentException("annotation/s cannot be null");
        }
        var instance = GuiceBootstrap.getInjectedInstance(AnnotationDisplay.class);
        if (instance == null) {
            throw new IllegalStateException("AnnotationDisplay not available via DI");
        }
        instance.annotationTable.getModel().addElement(ann);
    }

    public static void addAnnotations(Iterable<Annotation> anns) {
        if (anns == null) {
            throw new IllegalArgumentException("annotations cannot be null");
        }
        table.getModel().addElements(anns);
    }

    public static void removeAnnotation(int rowIndex) {
        table.getModel().removeElementAt(rowIndex);
    }

    public static void removeAllAnnotations() {
        table.getModel().removeAllElements();
    }

    public static AnnotationDisplay getInstance() {
        if (instance == null) {
            throw new IllegalStateException(
                    "AnnotationDisplay not initialized via DI. Ensure GuiceBootstrap.create() was"
                            + " called first.");
        }
        return instance;
    }

    public static int getNumAnnotations() {
        return table.getModel().size();
    }

    @Subscribe
    public void handleUIUpdateRequestedEvent(UIUpdateRequestedEvent event) {
        if (event.getComponent() == UIUpdateRequestedEvent.Component.ANNOTATION_DISPLAY) {
            repaint();
        }
    }
}
