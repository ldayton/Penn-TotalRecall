package ui.audiofiles;

import actions.ContinueAnnotatingAction;
import events.EventDispatchBus;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Factory for creating AudioFilePopupMenu instances. This allows multiple popup menus to be created
 * without singleton conflicts.
 */
@Singleton
public class AudioFilePopupMenuFactory {

    private final ContinueAnnotatingAction continueAnnotatingAction;
    private final state.AudioState audioState;
    private final EventDispatchBus eventBus;

    @Inject
    public AudioFilePopupMenuFactory(
            ContinueAnnotatingAction continueAnnotatingAction,
            state.AudioState audioState,
            EventDispatchBus eventBus) {
        this.continueAnnotatingAction = continueAnnotatingAction;
        this.audioState = audioState;
        this.eventBus = eventBus;
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
                new AudioFilePopupMenu(continueAnnotatingAction, audioState, eventBus);
        popupMenu.configureForFile(file, index);
        return popupMenu;
    }
}
