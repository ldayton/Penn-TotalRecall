package behaviors.singleact;

import components.wordpool.WordpoolDisplay;
import components.wordpool.WordpoolFileParser;
import components.wordpool.WordpoolWord;
import control.CurAudio;
import di.GuiceBootstrap;
import info.Constants;
import info.UserPrefs;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.List;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.DialogService;
import util.OSPath;

/**
 * Presents a file chooser to the user and then adds words from the selected file to the {@link
 * components.wordpool.WordpoolDisplay}.
 */
public class OpenWordpoolAction extends IdentifiedSingleAction {
    private static final Logger logger = LoggerFactory.getLogger(OpenWordpoolAction.class);

    public OpenWordpoolAction() {}

    @Override
    public void actionPerformed(ActionEvent arg0) {
        super.actionPerformed(arg0);
        var userManager = di.GuiceBootstrap.getInjectedInstance(env.UserManager.class);
        String maybeLastPath =
                UserPrefs.prefs.get(UserPrefs.openWordpoolPath, userManager.getUserHomeDir());
        if (new File(maybeLastPath).exists() == false) {
            maybeLastPath = userManager.getUserHomeDir();
        }

        String title = "Open Wordpool File";
        String path = null;
        // Get file chooser preference from LookAndFeelManager via DI
        env.LookAndFeelManager lafManager =
                di.GuiceBootstrap.getInjectedInstance(env.LookAndFeelManager.class);
        DialogService dialogService = GuiceBootstrap.getInjectedInstance(DialogService.class);
        if (lafManager.shouldUseAWTFileChoosers()) {
            if (dialogService == null) {
                throw new IllegalStateException("DialogService not available via DI");
            }
            File selectedFile =
                    dialogService.showFileOpenDialog(
                            title,
                            maybeLastPath,
                            new FilenameFilter() {
                                public boolean accept(File dir, String name) {
                                    return name.toLowerCase()
                                            .endsWith(Constants.wordpoolFileExtension);
                                }
                            });
            if (selectedFile != null) {
                path = selectedFile.getAbsolutePath();
            }
        } else {
            if (dialogService == null) {
                throw new IllegalStateException("DialogService not available via DI");
            }
            File selectedFile =
                    dialogService.showFileChooser(
                            title,
                            maybeLastPath,
                            JFileChooser.FILES_ONLY,
                            new FileFilter() {
                                @Override
                                public boolean accept(File f) {
                                    if (f.isDirectory()) {
                                        return true;
                                    }
                                    if (f.getName()
                                            .toLowerCase()
                                            .endsWith(Constants.wordpoolFileExtension)) {
                                        return true;
                                    } else {
                                        return false;
                                    }
                                }

                                @Override
                                public String getDescription() {
                                    return "Text (.txt) Files";
                                }
                            });
            if (selectedFile != null) {
                path = selectedFile.getPath();
            }
        }

        if (path != null) {
            File chosenFile = new File(path);
            if (chosenFile.isFile()) {
                UserPrefs.prefs.put(
                        UserPrefs.openWordpoolPath, new File(path).getParentFile().getPath());
                switchWordpool(chosenFile);
            }
        }
    }

    /** <code>OpenWordpoolAction</code> is always enabled. */
    @Override
    public void update() {}

    public static void switchWordpool(File file) {
        try {
            List<WordpoolWord> words = WordpoolFileParser.parse(file, false);
            WordpoolDisplay.removeAllWords();
            WordpoolDisplay.addWordpoolWords(words);

            if (CurAudio.audioOpen()) {
                File lstFile =
                        new File(
                                OSPath.basename(CurAudio.getCurrentAudioFileAbsolutePath())
                                        + "."
                                        + Constants.lstFileExtension);
                if (lstFile.exists()) {
                    try {
                        WordpoolDisplay.distinguishAsLst(WordpoolFileParser.parse(lstFile, true));
                    } catch (IOException e) {
                        logger.error("Failed to parse LST file: " + lstFile.getAbsolutePath(), e);
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Error processing wordpool file", e);
            DialogService dialogService = GuiceBootstrap.getInjectedInstance(DialogService.class);
            if (dialogService == null) {
                throw new IllegalStateException("DialogService not available via DI");
            }
            dialogService.showError("Cannot process wordpool file!");
        }
    }
}
