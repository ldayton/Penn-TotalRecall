package ui.layout;

import core.actions.impl.PlayPauseAction;
import core.dispatch.EventDispatchBus;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import ui.actions.ActionManager;
// import actions.AnnotateIntrusionAction;
// import actions.DeleteSelectedAnnotationAction; // Disabled - depends on WaveformDisplay
import ui.adapters.SwingActionRegistry;

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
            ui.viewport.ViewportCanvas waveformCanvas,
            ActionManager actionsManager,
            SwingActionRegistry swingActions,
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
        // On window resize, give extra space to the waveform (top)
        // so control panel padding remains visually constant
        setResizeWeight(1.0);

        // Make the divider thin, draggable, and without a visible handle/grip
        final int dividerPx = 3; // keep small but still usable on HiDPI
        setDividerSize(dividerPx);
        setBorder(BorderFactory.createEmptyBorder());
        setUI(
                new BasicSplitPaneUI() {
                    @Override
                    public BasicSplitPaneDivider createDefaultDivider() {
                        return new BasicSplitPaneDivider(this) {
                            private boolean hover;

                            {
                                // track hover to paint a stronger cue when resizable area is under
                                // mouse
                                addMouseListener(
                                        new MouseAdapter() {
                                            @Override
                                            public void mouseEntered(MouseEvent e) {
                                                hover = true;
                                                repaint();
                                            }

                                            @Override
                                            public void mouseExited(MouseEvent e) {
                                                hover = false;
                                                repaint();
                                            }
                                        });
                            }

                            @Override
                            public int getDividerSize() {
                                return dividerPx;
                            }

                            @Override
                            public void setBorder(Border b) {
                                /* no-op to keep flat */
                            }

                            @Override
                            protected JButton createLeftOneTouchButton() {
                                return zeroButton();
                            }

                            @Override
                            protected JButton createRightOneTouchButton() {
                                return zeroButton();
                            }

                            private JButton zeroButton() {
                                JButton b = new JButton();
                                b.setFocusable(false);
                                b.setBorder(BorderFactory.createEmptyBorder());
                                Dimension d = new Dimension(0, 0);
                                b.setPreferredSize(d);
                                b.setMinimumSize(d);
                                b.setMaximumSize(d);
                                return b;
                            }

                            @Override
                            public void paint(Graphics g) {
                                super.paint(g);
                                // Draw a subtle single-pixel separator line, no grip
                                g.setColor(getLineColor());
                                int w = getWidth();
                                int h = getHeight();
                                if (ContentSplitPane.this.getOrientation()
                                        == JSplitPane.HORIZONTAL_SPLIT) {
                                    int x = (w - 1) / 2;
                                    g.drawLine(x, 0, x, h);
                                } else {
                                    int y = (h - 1) / 2;
                                    g.drawLine(0, y, w, y);
                                }
                            }

                            private Color getLineColor() {
                                if (hover) {
                                    Color c = UIManager.getColor("Component.focusColor");
                                    if (c != null) return c;
                                }
                                Color sep = UIManager.getColor("Separator.foreground");
                                return sep != null ? sep : getForeground();
                            }
                        };
                    }
                });

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

        getActionMap().put("play", swingActions.get(PlayPauseAction.class));

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
