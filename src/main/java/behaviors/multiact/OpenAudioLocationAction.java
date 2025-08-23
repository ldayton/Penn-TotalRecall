package behaviors.multiact;

import components.audiofiles.AudioFileDisplay;
import di.GuiceBootstrap;
import env.PreferencesManager;
import info.Constants;
import info.PreferenceKeys;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FilenameFilter;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;
import util.DialogService;

/**
 * Presents a file chooser to the user and then adds the selected files to the {@link
 * components.audiofiles.AudioFileDisplay}.
 */
public class OpenAudioLocationAction extends IdentifiedMultiAction {

    public enum SelectionMode {
        FILES_ONLY,
        DIRECTORIES_ONLY,
        FILES_AND_DIRECTORIES
    }

    private final SelectionMode mode;

    public OpenAudioLocationAction(SelectionMode mode) {
        super(mode);
        this.mode = mode;
    }

    /**
     * Performs <code>Action</code> by attempting to open the file chooser on the directory the last
     * audio location selection was made in. Failing that, uses current directory. Afterwards adds
     * the selected files and requests the list be sorted.
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        super.actionPerformed(e);
        var userManager =
                GuiceBootstrap.getRequiredInjectedInstance(env.UserManager.class, "UserManager");
        var preferencesManager =
                GuiceBootstrap.getRequiredInjectedInstance(
                        PreferencesManager.class, "PreferencesManager");
        String maybeLastPath =
                preferencesManager.getString(
                        PreferenceKeys.OPEN_LOCATION_PATH, userManager.getUserHomeDir());
        if (new File(maybeLastPath).exists() == false) {
            maybeLastPath = userManager.getUserHomeDir();
        }

        String path = null;
        if (mode != SelectionMode.FILES_AND_DIRECTORIES) {
            String title;
            if (mode == SelectionMode.DIRECTORIES_ONLY) {
                System.setProperty(
                        "apple.awt.fileDialogForDirectories",
                        "true"); // exclusively directories then!
                title = "Open Audio Folder";
            } else {
                title = "Open Audio File";
            }
            DialogService dialogService = GuiceBootstrap.getInjectedInstance(DialogService.class);
            if (dialogService == null) {
                throw new IllegalStateException("DialogService not available via DI");
            }
            File selectedFile =
                    dialogService.showFileOpenDialog(
                            title,
                            maybeLastPath,
                            new FilenameFilter() {
                                public boolean accept(File dir, String name) {
                                    if (mode == SelectionMode.DIRECTORIES_ONLY) {
                                        return name == null;
                                    } else {
                                        for (String ext : Constants.audioFormatsLowerCase) {
                                            if (name.toLowerCase().endsWith(ext)) {
                                                return true;
                                            }
                                        }
                                        return false;
                                    }
                                }
                            });
            if (selectedFile != null) {
                path = selectedFile.getAbsolutePath();
            }
            System.setProperty("apple.awt.fileDialogForDirectories", "false");

        } else {
            DialogService dialogService = GuiceBootstrap.getInjectedInstance(DialogService.class);
            if (dialogService == null) {
                throw new IllegalStateException("DialogService not available via DI");
            }
            FileFilter fileFilter =
                    new FileFilter() {
                        @Override
                        public boolean accept(File f) {
                            if (f.isDirectory()) {
                                return true;
                            } else {
                                for (String ext : Constants.audioFormatsLowerCase) {
                                    if (f.getName().toLowerCase().endsWith(ext)) {
                                        return true;
                                    }
                                }
                                return false;
                            }
                        }

                        @Override
                        public String getDescription() {
                            return "Supported Audio Formats";
                        }
                    };

            File selectedFile =
                    dialogService.showFileChooser(
                            "Open Audio File or Folder",
                            maybeLastPath,
                            JFileChooser.FILES_AND_DIRECTORIES,
                            fileFilter);

            if (selectedFile != null) {
                path = selectedFile.getPath();
            }
        }
        if (path != null) {
            preferencesManager.putString(
                    PreferenceKeys.OPEN_LOCATION_PATH, new File(path).getParentFile().getPath());
            File chosenFile = new File(path);
            if (chosenFile.isFile()) {
                AudioFileDisplay.addFilesIfSupported(new File[] {chosenFile});
            } else if (chosenFile.isDirectory()) {
                AudioFileDisplay.addFilesIfSupported(chosenFile.listFiles());
            }
        }
    }

    /** <code>OpenAudioLocationAction</code> is always enabled. */
    @Override
    public void update() {}
}
