package core.actions.impl;

import core.actions.Action;
import core.dispatch.EventDispatchBus;
import core.env.PreferenceKeys;
import core.events.AudioFilesSelectedEvent;
import core.preferences.PreferencesManager;
import core.services.FileSelectionService;
import core.services.FileSelectionService.FileSelectionRequest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.File;

/**
 * Presents a directory chooser to the user for selecting audio folders and then publishes an event
 * with the files from that folder.
 */
@Singleton
public class OpenAudioFolderAction extends Action {

    private final PreferencesManager preferencesManager;
    private final FileSelectionService fileSelectionService;
    private final EventDispatchBus eventBus;

    @Inject
    public OpenAudioFolderAction(
            PreferencesManager preferencesManager,
            FileSelectionService fileSelectionService,
            EventDispatchBus eventBus) {
        this.preferencesManager = preferencesManager;
        this.fileSelectionService = fileSelectionService;
        this.eventBus = eventBus;
    }

    @Override
    public void execute() {
        String lastPath =
                preferencesManager.getString(
                        PreferenceKeys.OPEN_LOCATION_PATH, System.getProperty("user.home"));

        if (!new File(lastPath).exists()) {
            lastPath = System.getProperty("user.home");
        }

        var request =
                FileSelectionRequest.builder()
                        .title("Open Audio Folder")
                        .initialPath(lastPath)
                        .mode(FileSelectionRequest.SelectionMode.DIRECTORIES_ONLY)
                        .build();

        fileSelectionService
                .selectFile(request)
                .ifPresent(
                        folder -> {
                            preferencesManager.putString(
                                    PreferenceKeys.OPEN_LOCATION_PATH,
                                    folder.getParentFile().getPath());

                            if (folder.isDirectory()) {
                                File[] files = folder.listFiles();
                                if (files != null && files.length > 0) {
                                    eventBus.publish(new AudioFilesSelectedEvent(files));
                                }
                            }
                        });
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
