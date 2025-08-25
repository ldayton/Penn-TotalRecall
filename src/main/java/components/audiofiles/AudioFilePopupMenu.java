package components.audiofiles;

import actions.ContinueAnnotatingAction;
import control.AudioFileListEvent;
import control.AudioState;
import jakarta.inject.Inject;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import util.EventDispatchBus;

/**
 * <code>JPopupMenu</code> that presents user with actions for manipulating the <code>AudioFileList
 * </code>.
 *
 * <p>Different options are presented depending on the file state and the file/s the menu is being
 * launched on.
 */
public class AudioFilePopupMenu extends JPopupMenu {

    private final ContinueAnnotatingAction continueAnnotatingAction;
    private final AudioState audioState;
    private final EventDispatchBus eventBus;

    @Inject
    public AudioFilePopupMenu(
            ContinueAnnotatingAction continueAnnotatingAction,
            AudioState audioState,
            EventDispatchBus eventBus) {
        this.continueAnnotatingAction = continueAnnotatingAction;
        this.audioState = audioState;
        this.eventBus = eventBus;
    }

    /**
     * Constructs a popup menu with options appropriate for the provided file. Possible options
     * include marking the file incomplete, or removing it from the list. The popup menu will have
     * the <code>file</code> parameter as its title, regardless of whether the LAF officially
     * supports <code>JPopupMenu</code> titles.
     *
     * @param file The <code>AudioFile</code> on whose behalf the menu is being offered
     * @param index The index of <code>file</code> in its <code>AudioFileList</code>
     */
    public void configureForFile(AudioFile file, final int index) {
        removeAll(); // Clear existing items

        // most, if not all LAFs do not support JPopupMenu titles
        // to simulate a title we add a disabled JMenuItem
        JMenuItem fakeTitle = new JMenuItem(file.getName() + "...");
        fakeTitle.setEnabled(false);

        // Configure the injected action for this specific file
        continueAnnotatingAction.setAudioFile(file);
        JMenuItem cont = new JMenuItem(continueAnnotatingAction);
        if (file.isDone() == false) {
            cont.setEnabled(false);
        }

        JMenuItem del =
                new JMenuItem(
                        new AbstractAction() {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                eventBus.publish(
                                        new AudioFileListEvent(
                                                AudioFileListEvent.Type.REMOVE_FILE_AT_INDEX,
                                                index));
                            }
                        });
        del.setText("Remove from List");
        if (audioState.audioOpen()) {
            if (audioState.getCurrentAudioFileAbsolutePath().equals(file.getAbsolutePath())) {
                del.setEnabled(false);
            }
        }

        add(fakeTitle);
        addSeparator();
        add(cont);
        add(del);
    }
}
