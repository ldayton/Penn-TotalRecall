package actions;

import app.di.GuiceBootstrap;
import env.PreferenceKeys;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.ActionEvent;
import java.io.File;
import ui.DialogService;
import ui.audiofiles.AudioFileDisplay;
import ui.preferences.PreferencesManager;

/**
 * Presents a directory chooser to the user for selecting audio folders and then adds the selected
 * files to the {@link ui.audiofiles.AudioFileDisplay}.
 */
@Singleton
public class OpenAudioFolderAction extends BaseAction {

    private final PreferencesManager preferencesManager;

    @Inject
    public OpenAudioFolderAction(PreferencesManager preferencesManager) {
        super("Open Audio Folder", "Select audio folder");
        this.preferencesManager = preferencesManager;
    }

    /**
     * Performs <code>Action</code> by attempting to open the directory chooser on the directory the
     * last audio location selection was made in. Failing that, uses current directory. Afterwards
     * adds the selected files and requests the list be sorted.
     */
    @Override
    protected void performAction(ActionEvent e) {
        String maybeLastPath =
                preferencesManager.getString(
                        PreferenceKeys.OPEN_LOCATION_PATH, System.getProperty("user.home"));
        if (new File(maybeLastPath).exists() == false) {
            maybeLastPath = System.getProperty("user.home");
        }

        DialogService dialogService =
                GuiceBootstrap.getRequiredInjectedInstance(DialogService.class, "DialogService");
        File selectedFile =
                dialogService.showFileChooser(
                        "Open Audio Folder",
                        maybeLastPath,
                        javax.swing.JFileChooser.DIRECTORIES_ONLY,
                        null);
        if (selectedFile != null) {
            String path = selectedFile.getAbsolutePath();

            preferencesManager.putString(
                    PreferenceKeys.OPEN_LOCATION_PATH, new File(path).getParentFile().getPath());

            if (selectedFile.isDirectory()) {
                AudioFileDisplay.addFilesIfSupported(selectedFile.listFiles());
            }
        }
    }

    /** <code>OpenAudioFolderAction</code> is always enabled. */
    @Override
    public void update() {
        setEnabled(true);
    }
}
