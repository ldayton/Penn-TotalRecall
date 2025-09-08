package core.actions;

import core.dispatch.EventDispatchBus;
import core.env.Constants;
import core.env.PreferenceKeys;
import core.events.AudioFilesSelectedEvent;
import core.preferences.PreferencesManager;
import core.services.FileSelectionService;
import core.services.FileSelectionService.FileSelectionRequest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.File;
import java.util.HashSet;
import java.util.Set;

/** Opens a file selection dialog for audio files and publishes an event when files are selected. */
@Singleton
public class OpenAudioFileAction extends Action {

    private final PreferencesManager preferencesManager;
    private final FileSelectionService fileSelectionService;
    private final EventDispatchBus eventBus;

    @Inject
    public OpenAudioFileAction(
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

        // Validate path exists
        if (!new File(lastPath).exists()) {
            lastPath = System.getProperty("user.home");
        }

        Set<String> audioExtensions = new HashSet<>(Constants.audioFormatsLowerCase);

        var request =
                FileSelectionRequest.builder()
                        .title("Open Audio File")
                        .initialPath(lastPath)
                        .mode(FileSelectionRequest.SelectionMode.FILES_ONLY)
                        .filter("Supported Audio Formats", audioExtensions)
                        .build();

        fileSelectionService
                .selectFile(request)
                .ifPresent(
                        file -> {
                            // Save the directory for next time
                            preferencesManager.putString(
                                    PreferenceKeys.OPEN_LOCATION_PATH,
                                    file.getParentFile().getPath());

                            // Publish event for UI to handle
                            eventBus.publish(new AudioFilesSelectedEvent(file));
                        });
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String getLabel() {
        return "Open Audio File";
    }

    @Override
    public String getTooltip() {
        return "Select audio files";
    }
}
