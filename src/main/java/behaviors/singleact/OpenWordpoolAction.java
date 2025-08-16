package behaviors.singleact;

import components.MyFrame;
import components.wordpool.WordpoolDisplay;
import components.wordpool.WordpoolFileParser;
import components.wordpool.WordpoolWord;
import control.CurAudio;
import info.Constants;
import info.UserPrefs;
import java.awt.FileDialog;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.List;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.GiveMessage;
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
        if (lafManager.shouldUseAWTFileChoosers()) {
            FileDialog fd = new FileDialog(MyFrame.getInstance(), title);
            fd.setDirectory(maybeLastPath);
            fd.setFilenameFilter(
                    new FilenameFilter() {
                        public boolean accept(File dir, String name) {
                            return name.toLowerCase().endsWith(Constants.wordpoolFileExtension);
                        }
                    });
            fd.setVisible(true);
            path = fd.getDirectory() + fd.getFile();
        } else {
            JFileChooser jfc = new JFileChooser(maybeLastPath);
            jfc.setDialogTitle(title);
            jfc.setFileSelectionMode(JFileChooser.FILES_ONLY);
            jfc.setFileFilter(
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
            int result = jfc.showOpenDialog(MyFrame.getInstance());
            if (result == JFileChooser.APPROVE_OPTION) {
                path = jfc.getSelectedFile().getPath();
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
            GiveMessage.errorMessage("Cannot process wordpool file!");
        }
    }
}
