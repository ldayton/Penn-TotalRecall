package actions;

import env.PreferenceKeys;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.ActionEvent;
import java.io.File;
import javax.swing.JFileChooser;
import state.PreferencesManager;
import ui.audiofiles.AudioFileDisplay;

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

        JFileChooser fileChooser = new JFileChooser(maybeLastPath);
        fileChooser.setDialogTitle("Open Audio Folder");
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        int result = fileChooser.showOpenDialog(null);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
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
