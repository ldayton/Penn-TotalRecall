package components.audiofiles;

import actions.ContinueAnnotatingAction;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Factory for creating AudioFilePopupMenu instances. This allows multiple popup menus to be created
 * without singleton conflicts.
 */
@Singleton
public class AudioFilePopupMenuFactory {

    private final ContinueAnnotatingAction continueAnnotatingAction;

    @Inject
    public AudioFilePopupMenuFactory(ContinueAnnotatingAction continueAnnotatingAction) {
        this.continueAnnotatingAction = continueAnnotatingAction;
    }

    /**
     * Creates a new AudioFilePopupMenu instance configured for the given file.
     *
     * @param file The AudioFile for which to create the popup menu
     * @param index The index of the file in the list
     * @return A configured AudioFilePopupMenu instance
     */
    public AudioFilePopupMenu createPopupMenu(AudioFile file, int index) {
        AudioFilePopupMenu popupMenu = new AudioFilePopupMenu(continueAnnotatingAction);
        popupMenu.configureForFile(file, index);
        return popupMenu;
    }
}
