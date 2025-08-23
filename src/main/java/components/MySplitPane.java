package components;

import actions.ActionsManager;
import actions.PlayPauseAction;
import behaviors.multiact.AnnotateAction;
import behaviors.singleact.DeleteSelectedAnnotationAction;
import components.waveform.WaveformDisplay;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.KeyEvent;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;

/**
 * A custom <code>JSplitPane</code> that serves as the content pane to <code>MyFrame</code>. Splits
 * the program's interface between the waveform area above, and the control area below.
 */
@Singleton
public class MySplitPane extends JSplitPane {

    private static MySplitPane instance;
    private final ControlPanel controlPanel;
    private final WaveformDisplay waveformDisplay;
    private final ActionsManager actionsManager;
    private final PlayPauseAction playPauseAction;

    /**
     * Creates a new instance of the component, initializing internal components, key bindings,
     * listeners, and various aspects of appearance.
     */
    @Inject
    public MySplitPane(
            ControlPanel controlPanel,
            WaveformDisplay waveformDisplay,
            ActionsManager actionsManager,
            PlayPauseAction playPauseAction) {
        super(JSplitPane.VERTICAL_SPLIT, waveformDisplay, controlPanel);
        this.controlPanel = controlPanel;
        this.waveformDisplay = waveformDisplay;
        this.actionsManager = actionsManager;
        this.playPauseAction = playPauseAction;

        setOneTouchExpandable(
                false); // we don't want to make it easy to totally lost view of one of the
        // components, both are essential
        setContinuousLayout(
                true); // in general we want this true when audio is open, and closed otherwise, due
        // to expense of generated repaints
        setResizeWeight(0.5);

        // overrides MySplitPane key bindings for the benefit of SeekAction's key bindings and to
        // prevent accidental movement of the divider
        InputMap im = getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "none");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "none");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "none");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "none");

        DeleteSelectedAnnotationAction deleteAction = new DeleteSelectedAnnotationAction();
        InputMap deleteActionMap = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        String DELETE_ACTION_KEY = "delete action";
        deleteActionMap.put(actionsManager.lookup(deleteAction, null), DELETE_ACTION_KEY);
        getActionMap().put(DELETE_ACTION_KEY, deleteAction);
        actionsManager.registerInputMap(deleteAction, null, DELETE_ACTION_KEY, deleteActionMap);

        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, false), "play");

        getActionMap().put("play", playPauseAction);

        AnnotateAction intrusionAction = new AnnotateAction(AnnotateAction.Mode.INTRUSION);
        InputMap intrusionInputMap = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        String ANNOTATE_INTRUSION_KEY = "annotate intrusion";
        Enum<?> intrusionEnum = AnnotateAction.Mode.INTRUSION;
        KeyStroke intrusionKey = actionsManager.lookup(intrusionAction, intrusionEnum);
        intrusionInputMap.put(intrusionKey, ANNOTATE_INTRUSION_KEY);
        getActionMap().put(ANNOTATE_INTRUSION_KEY, intrusionAction);
        actionsManager.registerInputMap(
                intrusionAction, intrusionEnum, ANNOTATE_INTRUSION_KEY, intrusionInputMap);

        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_HOME, 0, false), "none");
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_END, 0, false), "none");

        // Set the singleton instance after full initialization
        instance = this;
    }

    /**
     * Singleton accessor.
     *
     * @return The singleton <code>MySplitPane</code>
     */
    public static MySplitPane getInstance() {
        if (instance == null) {
            throw new IllegalStateException(
                    "MySplitPane not initialized via DI. Ensure GuiceBootstrap.create() was called"
                            + " first.");
        }
        return instance;
    }
}
