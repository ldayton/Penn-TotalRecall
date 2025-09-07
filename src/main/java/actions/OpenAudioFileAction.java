package actions;

import app.swing.SwingApp;
import core.env.Constants;
import core.env.PreferenceKeys;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.ActionEvent;
import java.io.File;
import javax.swing.filechooser.FileFilter;
import ui.DialogService;
import ui.audiofiles.AudioFileDisplay;
import ui.preferences.PreferencesManager;

/**
 * Presents a file chooser to the user for selecting audio files only and then adds the selected
 * files to the {@link ui.audiofiles.AudioFileDisplay}.
 */
@Singleton
public class OpenAudioFileAction extends BaseAction {
    private final PreferencesManager preferencesManager;

    @Inject
    public OpenAudioFileAction(PreferencesManager preferencesManager) {
        super("Open Audio File", "Select audio files");
        this.preferencesManager = preferencesManager;
        setEnabled(true);
    }

    /**
     * Performs <code>Action</code> by attempting to open the file chooser on the directory the last
     * audio location selection was made in. Failing that, uses current directory. Afterwards adds
     * the selected files and requests the list be sorted.
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
                SwingApp.getRequiredInjectedInstance(DialogService.class, "DialogService");
        File selectedFile =
                dialogService.showFileChooser(
                        "Open Audio File",
                        maybeLastPath,
                        javax.swing.JFileChooser.FILES_ONLY,
                        new FileFilter() {
                            @Override
                            public boolean accept(File f) {
                                if (f.isDirectory()) return true;
                                for (String ext : Constants.audioFormatsLowerCase) {
                                    if (f.getName().toLowerCase().endsWith(ext)) return true;
                                }
                                return false;
                            }

                            @Override
                            public String getDescription() {
                                return "Supported Audio Formats";
                            }
                        });

        if (selectedFile != null) {
            String path = selectedFile.getAbsolutePath();

            preferencesManager.putString(
                    PreferenceKeys.OPEN_LOCATION_PATH, new File(path).getParentFile().getPath());

            if (selectedFile.isFile()) {
                AudioFileDisplay.addFilesIfSupported(new File[] {selectedFile});
            }
        }
    }
}
