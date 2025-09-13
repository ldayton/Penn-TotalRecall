package ui.audiofiles;

import core.dispatch.EventDispatchBus;
import core.dispatch.Subscribe;
import core.env.Constants;
import core.env.PreferenceKeys;
import core.events.AudioFilesSelectedEvent;
import core.preferences.PreferencesManager;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.Dimension;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ui.DialogService;
import ui.audiofiles.AudioFile.AudioFilePathException;

/**
 * A custom interface component for displaying the available audio files to the user.
 *
 * <p>Note: Access to this component from outside the package is limited to the public static
 * methods provided in this class. Code outside the package cannot and should not try to access the
 * internal list, model, or other components directly.
 */
@Singleton
public class AudioFileDisplay extends JScrollPane implements AudioFileDisplayInterface {
    private static final Logger logger = LoggerFactory.getLogger(AudioFileDisplay.class);

    private static final Dimension PREFERRED_SIZE = new Dimension(250, 180);

    private final PreferencesManager preferencesManager;
    private final DialogService dialogService;
    private final AudioFileList list;
    private final EventDispatchBus eventBus;

    /**
     * Creates a new instance of the component, initializing internal components, key bindings,
     * listeners, and various aspects of appearance.
     */
    @Inject
    public AudioFileDisplay(
            @NonNull AudioFileList audioFileList,
            @NonNull PreferencesManager preferencesManager,
            @NonNull EventDispatchBus eventBus,
            @NonNull DialogService dialogService) {
        this.preferencesManager = preferencesManager;
        this.dialogService = dialogService;
        this.list = audioFileList;
        this.eventBus = eventBus;
        getViewport().setView(list);

        setPreferredSize(PREFERRED_SIZE);
        // Allow vertical growth; keep initial width
        setMaximumSize(new Dimension(PREFERRED_SIZE.width, Integer.MAX_VALUE));

        // Remove LAF's outer border; the white area itself will draw its border.
        setBorder(BorderFactory.createEmptyBorder());
        setViewportBorder(BorderFactory.createEmptyBorder());

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
    @Override
    public boolean addFilesIfSupported(@NonNull File[] files) {
        var supportedFiles =
                Arrays.stream(files)
                        .filter(File::isFile)
                        .filter(f -> extensionSupported(f.getName()))
                        .map(
                                f -> {
                                    try {
                                        return new AudioFile(f.getAbsolutePath());
                                    } catch (AudioFilePathException e) {
                                        logger.error("Error updating audio file done status", e);
                                        dialogService.showError(e.getMessage());
                                        return null;
                                    }
                                })
                        .filter(Objects::nonNull)
                        .toList();

        if (!supportedFiles.isEmpty()) {
            list.getModel().addElements(supportedFiles);
            return true;
        }
        return false;
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
    @Override
    public boolean askToSwitchFile(@NonNull AudioFile file) {
        if (file.isDone()) {
            return false;
        }
        AudioFile currentFile = list.getCurrentAudioFile();
        if (currentFile != null) {
            if (file.getAbsolutePath().equals(currentFile.getAbsolutePath())) {
                return true;
            }
            var shouldWarn =
                    preferencesManager.getBoolean(
                            PreferenceKeys.WARN_FILE_SWITCH,
                            PreferenceKeys.DEFAULT_WARN_FILE_SWITCH);
            if (shouldWarn) {
                var message =
                        "Switch to file %s?\nYour changes to the current file will not be lost."
                                .formatted(file);
                var result = dialogService.showConfirmWithDontShowAgain(message);
                if (result.isDontShowAgain()) {
                    preferencesManager.putBoolean(PreferenceKeys.WARN_FILE_SWITCH, false);
                }
                if (!result.isConfirmed()) {
                    return false;
                }
            }
        }
        // Use the injected event bus to load the file
        eventBus.publish(new core.events.AudioFileLoadRequestedEvent(file.toFile()));

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
    private static boolean extensionSupported(@NonNull String name) {
        var lowerName = name.toLowerCase(Locale.ROOT);
        return Constants.audioFormatsLowerCase.stream().anyMatch(lowerName::endsWith);
    }

    @Subscribe
    public void handleAudioFilesSelected(@NonNull AudioFilesSelectedEvent event) {
        addFilesIfSupported(event.files());
    }
}
