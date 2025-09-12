package ui.audiofiles;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * MouseListener for the AudioFileList, used for launching popup context menus, and switching audio
 * file via double-click.
 */
@Singleton
public class AudioFileListMouseAdapter extends MouseAdapter {

    private final AudioFilePopupMenuFactory popupMenuFactory;
    private final AudioFileDisplayInterface audioFileDisplay;

    /**
     * Creates a mouse adapter that can act on the <code>AudioFileList</code> on whose behalf it is
     * listening.
     *
     * @param popupMenuFactory The factory for creating popup menus.
     * @param audioFileDisplay The audio file display for switching files.
     */
    @Inject
    public AudioFileListMouseAdapter(
            AudioFilePopupMenuFactory popupMenuFactory,
            AudioFileDisplayInterface audioFileDisplay) {
        this.popupMenuFactory = popupMenuFactory;
        this.audioFileDisplay = audioFileDisplay;
    }

    /**
     * Double clicks are used to switch the current audio file to the <code>AudioFile</code> clicked
     * on, if that file isn't done.
     *
     * @param e The <code>MouseEvent</code> provided by the action trigger
     */
    @Override
    public void mouseClicked(MouseEvent e) {
        AudioFileList list = (AudioFileList) e.getSource();
        AudioFile file = getAssociatedFile(e, list);
        if (file == null) {
            return; // event not on a File
        }
        if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
            audioFileDisplay.askToSwitchFile(file);
        }
    }

    /**
     * Some platforms launch context menu on press, and some on release, so control is passed to
     * {@link #evaluatePopup(MouseEvent)} for further consideration.
     *
     * @param e The <code>MouseEvent</code> provided by the action trigger
     */
    @Override
    public void mousePressed(MouseEvent e) {
        evaluatePopup(e);
    }

    /**
     * Some platforms launch context menu on press, and some on release, so control is passed to
     * {@link #evaluatePopup(MouseEvent)} for further consideration.
     *
     * @param e The <code>MouseEvent</code> provided by the action trigger
     */
    @Override
    public void mouseReleased(MouseEvent e) {
        evaluatePopup(e);
    }

    /**
     * Evaluates whether the mouse event is a popup trigger on this platform, and launches a popup
     * context menu if appropriate.
     *
     * @param e the mouse event to evaluate
     */
    public void evaluatePopup(MouseEvent e) {
        if (e.isPopupTrigger()) {
            AudioFileList list = (AudioFileList) e.getSource();
            AudioFile file = getAssociatedFile(e, list);
            if (file == null) {
                return; // event not on a File
            }
            AudioFilePopupMenu popupMenu =
                    popupMenuFactory.createPopupMenu(file, list.locationToIndex(e.getPoint()));
            popupMenu.show(e.getComponent(), e.getX(), e.getY());
        }
    }

    /**
     * Utility method for determining which <code>AudioFile</code> received the event.
     *
     * @param e The <code>MouseEvent</code> provided by the action trigger.
     * @param list The <code>AudioFileList</code> that received the event.
     * @return The <code>AudioFile</code> that received the mouse event, or <code>null</code> if the
     *     event was not on an <code>AudioFile</code>.
     */
    private AudioFile getAssociatedFile(MouseEvent e, AudioFileList list) {
        int index = list.locationToIndex(e.getPoint());
        if (index < 0) {
            return null; // event not on a File
        }
        AudioFile file = list.getModel().getElementAt(index);
        return file;
    }
}
