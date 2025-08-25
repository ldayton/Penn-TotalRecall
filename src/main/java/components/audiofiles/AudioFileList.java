package components.audiofiles;

import events.AudioFileListEvent;
import events.EventDispatchBus;
import events.Subscribe;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import state.AudioState;

/** A <code>JList</code> for displaying the available <code>AudioFiles</code>. */
@Singleton
public class AudioFileList extends JList<AudioFile> implements FocusListener {

    private static AudioFileList instance;

    private final AudioFileListModel model;
    private final AudioFileListCellRenderer render;
    private final AudioFileListMouseAdapter mouseAdapter;
    private final AudioState audioState;
    private final EventDispatchBus eventBus;

    /**
     * Constructs an <code>AudioFileList</code>, initializing mouse listeners, key bindings,
     * selection mode, cell renderer, and model.
     */
    @Inject
    public AudioFileList(
            AudioFileListMouseAdapter mouseAdapter,
            AudioState audioState,
            EventDispatchBus eventBus) {
        this.mouseAdapter = mouseAdapter;
        this.audioState = audioState;
        this.eventBus = eventBus;
        model = new AudioFileListModel();
        setModel(model);

        // set the cell renderer that will display incomplete/complete AudioFiles differently
        render = new AudioFileListCellRenderer(audioState);
        setCellRenderer(render);

        // at this point only one audio file can be selected a time, changing to multiple selection
        // mode would require
        // a (small) rewrite of popup menus, key bindings and mouse listeners
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        setLayoutOrientation(JList.VERTICAL);

        // this mouse listener handles context menus and double clicks to switch files
        addMouseListener(mouseAdapter);
        // focus listener makes the containing AudioFileDisplay look focused at the appropriate
        // times
        addFocusListener(this);

        // users can remove an AudioFile from the display by hitting delete or backspace (necessary
        // for mac which conflates the two)
        // technically this code is duplicated in the AudioFilePopupMenu code, but it's so simple
        // (one line after AudioFile is identified) that it's not worth
        // making a separate removal action that both will call
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0, false), "remove file");
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0, false), "remove file");
        getActionMap()
                .put(
                        "remove file",
                        new AbstractAction() {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                var selectedValues = getSelectedValuesList();
                                Object[] objs = selectedValues.toArray();
                                int index = getSelectedIndex();
                                if (index < 0) {
                                    return;
                                }
                                if (objs.length == 0) {
                                    return;
                                }

                                if (objs.length
                                        == 1) { // in case multiple selection mode is used in the
                                    // future
                                    AudioFile ff = (AudioFile) objs[0];
                                    if (ff == null) {
                                        return;
                                    }
                                    if (audioState.audioOpen()) {
                                        if (audioState
                                                .getCurrentAudioFileAbsolutePath()
                                                .equals(ff.getAbsolutePath())) {
                                            return;
                                        }
                                    }
                                    model.removeElementAt(index);
                                }
                            }
                        });
        // hitting enter can be used to switch to a file on the list
        // again, technically this code is duplicated by double click handler in
        // AudioFileListMouseAdapter, both the logic is too simple to justify
        // writing a separate action that both will call
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, false), "switch");
        getActionMap()
                .put(
                        "switch",
                        new AbstractAction() {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                var selectedValues = getSelectedValuesList();
                                Object[] objs = selectedValues.toArray();
                                if (objs.length
                                        == 1) { // in case multiple selection mode is used in the
                                    // future
                                    AudioFileDisplay.askToSwitchFile((AudioFile) objs[0]);
                                }
                            }
                        });

        // overrides JScrollPane key bindings for the benefit of SeekAction's key bindings
        getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0, false), "none");
        getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0, false), "none");
        getInputMap(JComponent.WHEN_FOCUSED)
                .put(
                        KeyStroke.getKeyStroke(
                                KeyEvent.VK_RIGHT, InputEvent.SHIFT_DOWN_MASK, false),
                        "none");
        getInputMap(JComponent.WHEN_FOCUSED)
                .put(
                        KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, InputEvent.SHIFT_DOWN_MASK, false),
                        "none");

        // since the AudioFileList is a clickable area, we must write focus handling code for the
        // event it is clicked on
        addMouseListener(
                new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent e) {
                        if (isFocusable()) {
                            // automatically takes focus in this case
                        } else {
                            getParent().requestFocusInWindow();
                        }
                    }
                });

        // Subscribe to events
        eventBus.subscribe(this);

        // Set the singleton instance after full initialization
        instance = this;
    }

    /**
     * Type-refined implementation that guarantees an <code>AudioFileListModel</code> instead of
     * <code>ListModel</code>
     *
     * @return The <code>AudioFileListModel</code> associated with the <code>AudioFileList</code>
     */
    @Override
    public AudioFileListModel getModel() {
        return model;
    }

    /**
     * Type-refined implementation that guarantees an <code>AudioFileListCellRenderer</code> instead
     * of <code>ListCellRenderer</code>
     *
     * @return The <code>AudioFileListCellRenderer</code> associated with the <code>AudioFileList
     *     </code>
     */
    @Override
    public AudioFileListCellRenderer getCellRenderer() {
        return render;
    }

    /**
     * Custom focusability condition that behaves in the default manner aside from rejecting focus
     * when this <code>AudioFileList</code> has no elements.
     *
     * @return Whether or nut this component should accept focus
     */
    @Override
    public boolean isFocusable() {
        return (super.isFocusable() && model.getSize() > 0);
    }

    /** Handler for the event that this <code>AudioFileList</code> gains focus. */
    @Override
    public void focusGained(FocusEvent e) {
        int anchor = getAnchorSelectionIndex();
        if (anchor >= 0) {
            setSelectedIndex(anchor);
        } else {
            setSelectedIndex(0);
        }
    }

    /** Handler for event that this <code>AudioFileList</code> loses focus. */
    @Override
    public void focusLost(FocusEvent e) {
        if (e.isTemporary() == false) {
            clearSelection();
        }
    }

    /**
     * Gets a reference to this object for use by a custom <code>FocusTraversalPolicy</code>.
     *
     * <p>Unfortunately this requires a break from the encapsulation strategy of <code>
     * AudioFileDisplay</code> containing all the <code>public</code> access. Please do NOT abuse
     * this method to access the <code>AudioFileList</code> for purposes other than those intended.
     * Add new public features to <code>AudioFileDisplay</code> which can then use {@linkplain
     * #getInstance()} as needed.
     *
     * @return {@link #getInstance()}
     */
    public static AudioFileList getFocusTraversalReference() {
        return getInstance();
    }

    /**
     * Singleton accessor.
     *
     * <p>Many classes in this package require access to this object, so a singleton accessor
     * strategy is used to avoid the need to pass every class a reference to this object.
     *
     * @return The singleton <code>AudioFileList</code>
     */
    protected static AudioFileList getInstance() {
        if (instance == null) {
            throw new IllegalStateException(
                    "AudioFileList not initialized via DI. Ensure GuiceBootstrap.create() was"
                            + " called first.");
        }
        return instance;
    }

    /** Handles AudioFileListEvent to remove files from the list. */
    @Subscribe
    public void handleAudioFileListEvent(AudioFileListEvent event) {
        if (event.getType() == AudioFileListEvent.Type.REMOVE_FILE_AT_INDEX) {
            // Check if the index is valid
            if (event.getIndex() >= 0 && event.getIndex() < model.getSize()) {
                // Don't remove if it's the currently playing file
                AudioFile fileToRemove = model.getElementAt(event.getIndex());
                if (audioState.audioOpen()
                        && audioState
                                .getCurrentAudioFileAbsolutePath()
                                .equals(fileToRemove.getAbsolutePath())) {
                    return; // Don't remove currently playing file
                }
                model.removeElementAt(event.getIndex());
            }
        }
    }
}
