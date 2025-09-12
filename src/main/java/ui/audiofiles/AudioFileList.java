package ui.audiofiles;

import com.google.inject.Provider;
import core.dispatch.EventDispatchBus;
import core.dispatch.Subscribe;
import core.events.AppStateChangedEvent;
import core.events.AudioFileListEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import lombok.NonNull;

/** A <code>JList</code> for displaying the available <code>AudioFiles</code>. */
@Singleton
public class AudioFileList extends JList<AudioFile> implements FocusListener {

    private final AudioFileListModel model;
    private final AudioFileListCellRenderer render;
    private final Provider<AudioFileDisplayInterface> audioFileDisplayProvider;
    private AudioFile currentAudioFile = null;

    /**
     * Constructs an <code>AudioFileList</code>, initializing mouse listeners, key bindings,
     * selection mode, cell renderer, and model.
     */
    @Inject
    public AudioFileList(
            @NonNull AudioFileListMouseAdapter mouseAdapter,
            @NonNull Provider<AudioFileDisplayInterface> audioFileDisplayProvider,
            @NonNull EventDispatchBus eventBus) {
        this.audioFileDisplayProvider = audioFileDisplayProvider;
        model = new AudioFileListModel();
        setModel(model);

        // set the cell renderer that will display incomplete/complete AudioFiles differently
        render = new AudioFileListCellRenderer();
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
                            public void actionPerformed(@NonNull ActionEvent e) {
                                var selectedValues = getSelectedValuesList();
                                if (selectedValues.size() != 1) {
                                    return;
                                }

                                var index = getSelectedIndex();
                                if (index < 0) {
                                    return;
                                }

                                var file = selectedValues.getFirst();
                                // Don't remove if it's the currently loaded file
                                if (currentAudioFile != null && currentAudioFile.equals(file)) {
                                    return;
                                }
                                model.removeElementAt(index);
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
                            public void actionPerformed(@NonNull ActionEvent e) {
                                var selectedValues = getSelectedValuesList();
                                if (selectedValues.size() == 1) {
                                    audioFileDisplayProvider
                                            .get()
                                            .askToSwitchFile(selectedValues.getFirst());
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
                    public void mousePressed(@NonNull MouseEvent e) {
                        if (isFocusable()) {
                            // automatically takes focus in this case
                        } else {
                            getParent().requestFocusInWindow();
                        }
                    }
                });

        // Subscribe to events
        eventBus.subscribe(this);
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
    public void focusGained(@NonNull FocusEvent e) {
        int anchor = getAnchorSelectionIndex();
        if (anchor >= 0) {
            setSelectedIndex(anchor);
        } else {
            setSelectedIndex(0);
        }
    }

    /** Handler for event that this <code>AudioFileList</code> loses focus. */
    @Override
    public void focusLost(@NonNull FocusEvent e) {
        if (e.isTemporary() == false) {
            clearSelection();
        }
    }

    @Subscribe
    public void onAppStateChanged(@NonNull AppStateChangedEvent event) {
        if (event.isAudioLoaded() && event.context() instanceof File loadedFile) {
            for (int i = 0; i < model.getSize(); i++) {
                var audioFile = model.getElementAt(i);
                if (audioFile.getAbsolutePath().equals(loadedFile.getAbsolutePath())) {
                    currentAudioFile = audioFile;
                    setSelectedIndex(i);
                    ensureIndexIsVisible(i);
                    repaint(); // Force renderer to update
                    break;
                }
            }
        } else if (event.isAudioClosed()) {
            currentAudioFile = null;
            clearSelection();
            repaint(); // Force renderer to update
        }
    }

    /** Gets the currently loaded audio file for rendering purposes. */
    public AudioFile getCurrentAudioFile() {
        return currentAudioFile;
    }

    /** Handles AudioFileListEvent to remove files from the list. */
    @Subscribe
    public void handleAudioFileListEvent(@NonNull AudioFileListEvent event) {
        if (event.type() == AudioFileListEvent.Type.REMOVE_FILE_AT_INDEX) {
            // Check if the index is valid
            if (event.index() >= 0 && event.index() < model.getSize()) {
                // Don't remove if it's the currently playing file
                var fileToRemove = model.getElementAt(event.index());
                if (currentAudioFile != null && currentAudioFile.equals(fileToRemove)) {
                    return; // Don't remove currently playing file
                }
                model.removeElementAt(event.index());
            }
        }
    }
}
