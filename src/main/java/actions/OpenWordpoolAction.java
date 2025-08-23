package actions;

import components.wordpool.WordpoolDisplay;
import components.wordpool.WordpoolFileParser;
import components.wordpool.WordpoolWord;
import control.AudioState;
import control.ErrorRequestedEvent;
import env.PreferencesManager;
import env.UserManager;
import info.Constants;
import info.PreferenceKeys;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.util.List;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.EventBus;
import util.OSPath;

/**
 * Presents a file chooser to the user and then adds words from the selected file to the {@link
 * components.wordpool.WordpoolDisplay}.
 */
@Singleton
public class OpenWordpoolAction extends BaseAction {
    private static final Logger logger = LoggerFactory.getLogger(OpenWordpoolAction.class);

    private final AudioState audioState;
    private final PreferencesManager preferencesManager;
    private final UserManager userManager;
    private final EventBus eventBus;

    @Inject
    public OpenWordpoolAction(
            AudioState audioState,
            PreferencesManager preferencesManager,
            UserManager userManager,
            EventBus eventBus) {
        super("Open Wordpool...", "Load words from a text file into the wordpool");
        this.audioState = audioState;
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

        JFileChooser fileChooser = new JFileChooser(maybeLastPath);
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setDialogTitle("Open Wordpool File");
        fileChooser.setFileFilter(
                new FileFilter() {
                    @Override
                    public boolean accept(File f) {
                        if (f.isDirectory()) {
                            return true;
                        }
                        return f.getName().toLowerCase().endsWith(Constants.wordpoolFileExtension);
                    }

                    @Override
                    public String getDescription() {
                        return "Text (.txt) Files";
                    }
                });

        int result = fileChooser.showOpenDialog(null);
        if (result == JFileChooser.APPROVE_OPTION) {
            File chosenFile = fileChooser.getSelectedFile();
            if (chosenFile != null && chosenFile.isFile()) {
                preferencesManager.putString(
                        PreferenceKeys.OPEN_WORDPOOL_PATH, chosenFile.getParentFile().getPath());
                switchWordpool(chosenFile);
            }
        }
    }

    /** OpenWordpoolAction is always enabled. */
    @Override
    public void update() {}

    public void switchWordpool(File file) {
        try {
            List<WordpoolWord> words = WordpoolFileParser.parse(file, false);
            WordpoolDisplay.removeAllWords();
            WordpoolDisplay.addWordpoolWords(words);

            if (audioState.audioOpen()) {
                File lstFile =
                        new File(
                                OSPath.basename(audioState.getCurrentAudioFileAbsolutePath())
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
            eventBus.publish(new ErrorRequestedEvent("Cannot process wordpool file!"));
        }
    }
}
