package ui.audiofiles;

// import actions.ContinueAnnotatingAction;
import core.dispatch.EventDispatchBus;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

/**
 * Factory for creating AudioFilePopupMenu instances. This allows multiple popup menus to be created
 * without singleton conflicts.
 */
@Singleton
public class AudioFilePopupMenuFactory {

    // private final ContinueAnnotatingAction continueAnnotatingAction;
    private final EventDispatchBus eventBus;
    private final Provider<AudioFileList> audioFileListProvider;

    @Inject
    public AudioFilePopupMenuFactory(
            // ContinueAnnotatingAction continueAnnotatingAction,
            EventDispatchBus eventBus, Provider<AudioFileList> audioFileListProvider) {
        // this.continueAnnotatingAction = continueAnnotatingAction;
        this.eventBus = eventBus;
        this.audioFileListProvider = audioFileListProvider;
    }

    /**
     * Creates a new AudioFilePopupMenu instance configured for the given file.
     *
     * @param file The AudioFile for which to create the popup menu
     * @param index The index of the file in the list
     * @return A configured AudioFilePopupMenu instance
     */
    public AudioFilePopupMenu createPopupMenu(AudioFile file, int index) {
        AudioFilePopupMenu popupMenu =
                new AudioFilePopupMenu(/*continueAnnotatingAction,*/ eventBus);

        // Check if this file is the currently loaded one
        AudioFile currentFile = audioFileListProvider.get().getCurrentAudioFile();
        boolean isCurrentFile =
                currentFile != null && currentFile.getAbsolutePath().equals(file.getAbsolutePath());

        popupMenu.configureForFile(file, index, isCurrentFile);
        return popupMenu;
    }
}
