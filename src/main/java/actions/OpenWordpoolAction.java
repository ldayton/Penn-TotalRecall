package actions;

import core.dispatch.EventDispatchBus;
import core.env.Constants;
import core.env.PreferenceKeys;
import core.env.UserHomeProvider;
import core.events.WordpoolFileSelectedEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.ActionEvent;
import java.io.File;
import javax.swing.filechooser.FileFilter;
import ui.preferences.PreferencesManager;

/**
 * Presents a file chooser to the user and then publishes an event to load words from the selected
 * file.
 */
@Singleton
public class OpenWordpoolAction extends BaseAction {

    private final PreferencesManager preferencesManager;
    private final UserHomeProvider userManager;
    private final EventDispatchBus eventBus;

    @Inject
    public OpenWordpoolAction(
            PreferencesManager preferencesManager,
            UserHomeProvider userManager,
            EventDispatchBus eventBus) {
        super("Open Wordpool...", "Load words from a text file into the wordpool");
        this.preferencesManager = preferencesManager;
        this.userManager = userManager;
        this.eventBus = eventBus;
    }

    @Override
    protected void performAction(ActionEvent arg0) {
        String maybeLastPath =
                preferencesManager.getString(
                        PreferenceKeys.OPEN_WORDPOOL_PATH, userManager.getUserHomeDir());
        if (new File(maybeLastPath).exists() == false) {
            maybeLastPath = userManager.getUserHomeDir();
        }

        File chosenFile =
                ui.DialogService.class
                        .cast(
                                app.swing.SwingApp.getRequiredInjectedInstance(
                                        ui.DialogService.class, "DialogService"))
                        .showFileChooser(
                                "Open Wordpool File",
                                maybeLastPath,
                                javax.swing.JFileChooser.FILES_ONLY,
                                new FileFilter() {
                                    @Override
                                    public boolean accept(File f) {
                                        if (f.isDirectory()) return true;
                                        return f.getName()
                                                .toLowerCase()
                                                .endsWith(Constants.wordpoolFileExtension);
                                    }

                                    @Override
                                    public String getDescription() {
                                        return "Text (.txt) Files";
                                    }
                                });

        if (chosenFile != null && chosenFile.isFile()) {
            preferencesManager.putString(
                    PreferenceKeys.OPEN_WORDPOOL_PATH, chosenFile.getParentFile().getPath());
            eventBus.publish(new WordpoolFileSelectedEvent(chosenFile));
        }
    }
}
