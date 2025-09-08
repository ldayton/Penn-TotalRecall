package ui.layout;

import core.actions.PlayPauseAction;
import core.dispatch.EventDispatchBus;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.KeyEvent;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;
import ui.actions.ActionsManager;
// import actions.AnnotateIntrusionAction;
// import actions.DeleteSelectedAnnotationAction; // Disabled - depends on WaveformDisplay
import ui.adapters.SwingAction;

/**
 * A custom <code>JSplitPane</code> that serves as the content pane to <code>MainFrame</code>.
 * Splits the program's interface between the waveform area above, and the control area below.
 */
@Singleton
public class ContentSplitPane extends JSplitPane {

    /**
     * Creates a new instance of the component, initializing internal components, key bindings,
     * listeners, and various aspects of appearance.
     */
    @Inject
    public ContentSplitPane(
            ControlPanel controlPanel,
            WaveformCanvas waveformCanvas,
            ActionsManager actionsManager,
            PlayPauseAction playPauseAction,
            // DeleteSelectedAnnotationAction deleteSelectedAnnotationAction, // Disabled
            // AnnotateIntrusionAction annotateIntrusionAction,
            EventDispatchBus eventBus) {
        super(JSplitPane.VERTICAL_SPLIT, waveformCanvas, controlPanel);
        setOneTouchExpandable(
                false); // we don't want to make it easy to totally lost view of one of the
        // components, both are essential
        setContinuousLayout(
                true); // in general we want this true when audio is open, and closed otherwise, due
        // to expense of generated repaints
        setResizeWeight(0.5);

        // overrides ContentSplitPane key bindings for the benefit of SeekAction's key bindings and
        // to
        // prevent accidental movement of the divider
        InputMap im = getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "none");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "none");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "none");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "none");

        // DeleteSelectedAnnotationAction disabled
        // InputMap deleteActionMap = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        // String DELETE_ACTION_KEY = "delete action";
        // deleteActionMap.put(
        //         actionsManager.lookup(deleteSelectedAnnotationAction, null), DELETE_ACTION_KEY);
        // getActionMap().put(DELETE_ACTION_KEY, deleteSelectedAnnotationAction);
        // actionsManager.registerInputMap(
        //         deleteSelectedAnnotationAction, null, DELETE_ACTION_KEY, deleteActionMap);

        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, false), "play");

        getActionMap().put("play", new SwingAction(playPauseAction));

        // Annotation intrusion action disabled
        // InputMap intrusionInputMap = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        // String ANNOTATE_INTRUSION_KEY = "annotate intrusion";
        // KeyStroke intrusionKey = actionsManager.lookup(annotateIntrusionAction, null);
        // intrusionInputMap.put(intrusionKey, ANNOTATE_INTRUSION_KEY);
        // getActionMap().put(ANNOTATE_INTRUSION_KEY, annotateIntrusionAction);
        // actionsManager.registerInputMap(
        //         annotateIntrusionAction, null, ANNOTATE_INTRUSION_KEY, intrusionInputMap);

        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_HOME, 0, false), "none");
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_END, 0, false), "none");
    }
}
