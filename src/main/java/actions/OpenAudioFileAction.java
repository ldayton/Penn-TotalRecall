package actions;

import components.audiofiles.AudioFileDisplay;
import control.AudioState;
import env.PreferencesManager;
import info.Constants;
import info.PreferenceKeys;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.ActionEvent;
import java.io.File;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;
import util.EventDispatchBus;

/**
 * Presents a file chooser to the user for selecting audio files only and then adds the selected
 * files to the {@link components.audiofiles.AudioFileDisplay}.
 */
@Singleton
public class OpenAudioFileAction extends BaseAction {

    private final AudioState audioState;
    private final EventDispatchBus eventBus;
    private final PreferencesManager preferencesManager;

    @Inject
    public OpenAudioFileAction(
            AudioState audioState,
            EventDispatchBus eventBus,
            PreferencesManager preferencesManager) {
        super("Open Audio File", "Select audio files");
        this.audioState = audioState;
        this.eventBus = eventBus;
        this.preferencesManager = preferencesManager;
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

        JFileChooser fileChooser = new JFileChooser(maybeLastPath);
        fileChooser.setDialogTitle("Open Audio File");
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

        fileChooser.setFileFilter(
                new FileFilter() {
                    @Override
                    public boolean accept(File f) {
                        if (f.isDirectory()) {
                            return true;
                        }
                        for (String ext : Constants.audioFormatsLowerCase) {
                            if (f.getName().toLowerCase().endsWith(ext)) {
                                return true;
                            }
                        }
                        return false;
                    }

                    @Override
                    public String getDescription() {
                        return "Supported Audio Formats";
                    }
                });

        int result = fileChooser.showOpenDialog(null);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            String path = selectedFile.getAbsolutePath();

            preferencesManager.putString(
                    PreferenceKeys.OPEN_LOCATION_PATH, new File(path).getParentFile().getPath());

            if (selectedFile.isFile()) {
                AudioFileDisplay.addFilesIfSupported(new File[] {selectedFile});
            }
        }
    }

    /** <code>OpenAudioFileAction</code> is always enabled. */
    @Override
    public void update() {
        setEnabled(true);
    }
}
