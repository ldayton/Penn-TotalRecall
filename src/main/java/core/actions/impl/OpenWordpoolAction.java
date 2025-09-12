package core.actions.impl;

import core.actions.Action;
import core.dispatch.EventDispatchBus;
import core.env.Constants;
import core.env.PreferenceKeys;
import core.env.UserHomeProvider;
import core.events.WordpoolFileSelectedEvent;
import core.preferences.PreferencesManager;
import core.services.FileSelectionService;
import core.services.FileSelectionService.FileSelectionRequest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.File;
import java.util.Set;

/**
 * Presents a file chooser to the user and then publishes an event to load words from the selected
 * file.
 */
@Singleton
public class OpenWordpoolAction extends Action {

    private final PreferencesManager preferencesManager;
    private final UserHomeProvider userManager;
    private final EventDispatchBus eventBus;
    private final FileSelectionService fileSelectionService;

    @Inject
    public OpenWordpoolAction(
            PreferencesManager preferencesManager,
            UserHomeProvider userManager,
            EventDispatchBus eventBus,
            FileSelectionService fileSelectionService) {
        this.preferencesManager = preferencesManager;
        this.userManager = userManager;
        this.eventBus = eventBus;
        this.fileSelectionService = fileSelectionService;
    }

    @Override
    public void execute() {
        String lastPath =
                preferencesManager.getString(
                        PreferenceKeys.OPEN_WORDPOOL_PATH, userManager.getUserHomeDir());

        if (!new File(lastPath).exists()) {
            lastPath = userManager.getUserHomeDir();
        }

        var request =
                FileSelectionRequest.builder()
                        .title("Open Wordpool File")
                        .initialPath(lastPath)
                        .mode(FileSelectionRequest.SelectionMode.FILES_ONLY)
                        .filter("Text (.txt) Files", Set.of(Constants.wordpoolFileExtension))
                        .build();

        fileSelectionService
                .selectFile(request)
                .ifPresent(
                        file -> {
                            if (file.isFile()) {
                                preferencesManager.putString(
                                        PreferenceKeys.OPEN_WORDPOOL_PATH,
                                        file.getParentFile().getPath());
                                eventBus.publish(new WordpoolFileSelectedEvent(file));
                            }
                        });
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
