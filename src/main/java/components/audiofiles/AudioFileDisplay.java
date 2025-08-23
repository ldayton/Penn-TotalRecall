package components.audiofiles;

import components.audiofiles.AudioFile.AudioFilePathException;
import control.CurAudio;
import di.GuiceBootstrap;
import info.Constants;
import info.GUIConstants;
import info.MyShapes;
import info.UserPrefs;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Locale;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.DialogService;

/**
 * A custom interface component for displaying the available audio files to the user.
 *
 * <p>Note: Access to this component from outside the package is limited to the public static
 * methods provided in this class. Code outside the package cannot and should not try to access the
 * internal list, model, or other components directly.
 */
@Singleton
public class AudioFileDisplay extends JScrollPane {
    private static final Logger logger = LoggerFactory.getLogger(AudioFileDisplay.class);

    private static final String title = "Audio Files";

    private static AudioFileDisplay instance;
    private final AudioFileList audioFileList;

    private static AudioFileList list;

    /**
     * Creates a new instance of the component, initializing internal components, key bindings,
     * listeners, and various aspects of appearance.
     */
    @SuppressWarnings("StaticAssignmentInConstructor")
    @Inject
    public AudioFileDisplay(AudioFileList audioFileList) {
        this.audioFileList = audioFileList;
        list = audioFileList;
        getViewport().setView(list);

        setPreferredSize(GUIConstants.soundFileDisplayDimension);
        setMaximumSize(GUIConstants.soundFileDisplayDimension);

        setBorder(MyShapes.createMyUnfocusedTitledBorder(title));

        // overrides JScrollPane key bindings for the benefit of SeekAction's key bindings
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0, false), "none");
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0, false), "none");

        // since AudioFileDisplay is a clickable area, we must write focus handling code for the
        // event it is clicked on
        // this case is rare, since only a very small amount of this component is exposed (the area
        // around the border title), the rest being obscured by the AudioFileList
        // JScrollPane passes focus to JList if focusable, and to the frame otherwise
        addMouseListener(
                new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent e) {
                        if (list.isFocusable()) {
                            list.requestFocusInWindow();
                        } else {
                            getParent().requestFocusInWindow();
                        }
                    }
                });

        // Set the singleton instance after full initialization
        instance = this;
    }

    /**
     * Adds files to the <code>AudioFileList</code>, but only if ones that are regular files with
     * supported file extensions. <code>AudioFiles</code> that don't exist, or are already displayed
     * in this component are automatically filtered out, so this does not need to be checked in
     * advance by the caller.
     *
     * @param files Candidate files to be added to the <code>AudioFileList</code>
     * @return <code>true</code> if any of the files were ultimately added
     */
    public static boolean addFilesIfSupported(File[] files) {
        ArrayList<AudioFile> supportedFiles = new ArrayList<AudioFile>();
        for (File f : files) {
            if (f.isFile()) { // this also filters files that don't actually exist, filtering of
                // duplicate files is handled by the AudioFileListModel
                if (extensionSupported(f.getName())) {
                    AudioFile af;
                    try {
                        af = new AudioFile(f.getAbsolutePath());
                    } catch (AudioFilePathException e) {
                        logger.error("Error updating audio file done status", e);
                        DialogService dialogService =
                                GuiceBootstrap.getInjectedInstance(DialogService.class);
                        if (dialogService == null) {
                            throw new IllegalStateException("DialogService not available via DI");
                        }
                        dialogService.showError(e.getMessage());
                        continue;
                    }
                    supportedFiles.add(af);
                }
            }
        }
        if (supportedFiles.size() > 0) {
            list.getModel().addElements(supportedFiles);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Singleton accessor.
     *
     * @return The singleton <code>AudioFileDisplay</code>
     */
    public static AudioFileDisplay getInstance() {
        if (instance == null) {
            throw new IllegalStateException(
                    "AudioFileDisplay not initialized via DI. Ensure GuiceBootstrap.create() was"
                            + " called first.");
        }
        return instance;
    }

    /**
     * Switches to the provided <code>File</code>, but only after asking the user for confirmation
     * if the current user's preferences demand such a warning.
     *
     * <p>Keep in mind the user may decline to switch file. Also, if the provided file is already
     * done being annotated, the switch will automatically be rejected without user being prompted.
     *
     * @param file The file that may be switched to
     * @return <code>true</code> iff the file switch took place
     */
    protected static boolean askToSwitchFile(AudioFile file) {
        if (file.isDone()) {
            return false;
        }
        if (CurAudio.audioOpen()) {
            if (file.getAbsolutePath().equals(CurAudio.getCurrentAudioFileAbsolutePath())) {
                return true;
            }
            boolean shouldWarn =
                    UserPrefs.prefs.getBoolean(
                            UserPrefs.warnFileSwitch, UserPrefs.defaultWarnFileSwitch);
            if (shouldWarn) {
                JCheckBox checkbox = new JCheckBox(GUIConstants.dontShowAgainString);
                String message =
                        "Switch to file "
                                + file
                                + "?\nYour changes to the current file will not be lost.";
                Object[] params = {message, checkbox};
                int response =
                        JOptionPane.showConfirmDialog(
                                null,
                                params,
                                GUIConstants.yesNoDialogTitle,
                                JOptionPane.YES_NO_OPTION);
                boolean dontShow = checkbox.isSelected();
                if (dontShow && response != JOptionPane.CLOSED_OPTION) {
                    UserPrefs.prefs.putBoolean(UserPrefs.warnFileSwitch, false);
                }
                if (response != JOptionPane.YES_OPTION) {
                    return false;
                }
            }
        }
        CurAudio.switchFile(file);
        return true;
    }

    /**
     * Decides whether a <code>File</code> is supported by comparing its extension to the collection
     * of supported extensions in {@link Constants#audioFormatsLowerCase}, ignoring case.
     *
     * @param name The name of the file, from <code>File.getName()</code>
     * @return <code>true</code> iff the the <code>name</code> parameter ends with a supported
     *     extension
     */
    private static boolean extensionSupported(String name) {
        for (String ext : Constants.audioFormatsLowerCase) {
            if (name.toLowerCase(Locale.ROOT).endsWith(ext)) {
                return true;
            }
        }
        return false;
    }
}
