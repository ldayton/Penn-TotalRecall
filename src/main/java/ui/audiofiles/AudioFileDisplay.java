package ui.audiofiles;

import app.swing.SwingApp;
import env.Constants;
import env.PreferenceKeys;
import events.EventDispatchBus;
import events.Subscribe;
import events.UIUpdateRequestedEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.Dimension;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ui.DialogService;
import ui.audiofiles.AudioFile.AudioFilePathException;
import ui.preferences.PreferencesManager;

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
    private static final Dimension PREFERRED_SIZE = new Dimension(250, Integer.MAX_VALUE);

    private static AudioFileDisplay instance;
    private final PreferencesManager preferencesManager;

    private static AudioFileList list;

    /**
     * Creates a new instance of the component, initializing internal components, key bindings,
     * listeners, and various aspects of appearance.
     */
    @SuppressWarnings("StaticAssignmentInConstructor")
    @Inject
    public AudioFileDisplay(
            AudioFileList audioFileList,
            PreferencesManager preferencesManager,
            EventDispatchBus eventBus) {
        this.preferencesManager = preferencesManager;
        list = audioFileList;
        getViewport().setView(list);

        setPreferredSize(PREFERRED_SIZE);
        setMaximumSize(PREFERRED_SIZE);

        setBorder(BorderFactory.createTitledBorder(title));

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

        // Subscribe to UI update events
        eventBus.subscribe(this);
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
                                SwingApp.getInjectedInstance(DialogService.class);
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
        AudioFile currentFile = list.getCurrentAudioFile();
        if (currentFile != null) {
            if (file.getAbsolutePath().equals(currentFile.getAbsolutePath())) {
                return true;
            }
            boolean shouldWarn =
                    instance.preferencesManager.getBoolean(
                            PreferenceKeys.WARN_FILE_SWITCH,
                            PreferenceKeys.DEFAULT_WARN_FILE_SWITCH);
            if (shouldWarn) {
                String message =
                        "Switch to file "
                                + file
                                + "?\nYour changes to the current file will not be lost.";
                DialogService dialogService =
                        SwingApp.getRequiredInjectedInstance(DialogService.class, "DialogService");
                var result = dialogService.showConfirmWithDontShowAgain(message);
                if (result.isDontShowAgain()) {
                    instance.preferencesManager.putBoolean(PreferenceKeys.WARN_FILE_SWITCH, false);
                }
                if (!result.isConfirmed()) {
                    return false;
                }
            }
        }
        // Use the new event-driven system to load the file
        var eventBus =
                SwingApp.getRequiredInjectedInstance(
                        events.EventDispatchBus.class, "EventDispatchBus");
        eventBus.publish(new events.AudioFileLoadRequestedEvent(file));

        // UI updates are now handled via events
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

    @Subscribe
    public void handleUIUpdateRequestedEvent(UIUpdateRequestedEvent event) {
        if (event.getComponent() == UIUpdateRequestedEvent.Component.AUDIO_FILE_DISPLAY) {
            repaint();
        }
    }
}
