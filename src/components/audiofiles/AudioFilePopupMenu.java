package components.audiofiles;

import behaviors.singleact.ContinueAnnotatingAction;
import control.CurAudio;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

/**
 * <code>JPopupMenu</code> that presents user with actions for manipulating the <code>AudioFileList
 * </code>.
 *
 * <p>Different options are presented depending on the file state and the file/s the menu is being
 * launched on.
 */
public class AudioFilePopupMenu extends JPopupMenu {

    /**
     * Constructs a popup menu with options appropriate for the provided file. Possible options
     * include marking the file incomplete, or removing it from the list. The popup menu will have
     * the <code>file</code> parameter as its title, regardless of whether the LAF officially
     * supports <code>JPopupMenu</code> titles.
     *
     * @param file The <code>AudioFile</code> on whose behalf the menu is being offered
     * @param index The index of <code>file</code> in its <code>AudioFileList</code>
     */
    protected AudioFilePopupMenu(AudioFile file, final int index) {
        super();

        // most, if not all LAFs do not support JPopupMenu titles
        // to simulate a title we add a disabled JMenuItem
        JMenuItem fakeTitle = new JMenuItem(file.getName() + "...");
        fakeTitle.setEnabled(false);

        JMenuItem cont = new JMenuItem(new ContinueAnnotatingAction(file));
        if (file.isDone() == false) {
            cont.setEnabled(false);
        }
        JMenuItem del =
                new JMenuItem(
                        new AbstractAction() {
                            public void actionPerformed(ActionEvent e) {
                                AudioFileList.getInstance().getModel().removeElementAt(index);
                            }
                        });
        del.setText("Remove from List");
        if (CurAudio.audioOpen()) {
            if (CurAudio.getCurrentAudioFileAbsolutePath().equals(file.getAbsolutePath())) {
                del.setEnabled(false);
            }
        }

        add(fakeTitle);
        addSeparator();
        add(cont);
        add(del);
    }
}
