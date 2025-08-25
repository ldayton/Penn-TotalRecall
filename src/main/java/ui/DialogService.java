package ui;

import actions.AboutAction;
import components.MainFrame;
import env.Constants;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.FileDialog;
import java.io.File;
import java.io.FilenameFilter;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import lombok.NonNull;

/**
 * Service for managing all dialog operations in the application.
 *
 * <p>Centralizes dialog creation for better dependency injection and testability.
 */
@Singleton
public class DialogService {

    private final MainFrame mainFrame;
    private final ImageIcon appIcon;

    @Inject
    public DialogService(@NonNull MainFrame mainFrame) {
        this.mainFrame = mainFrame;
        this.appIcon = new ImageIcon(AboutAction.class.getResource("/images/headphones48.png"));
    }

    /**
     * Shows an error dialog with the provided message.
     *
     * @param message The error message to display
     */
    public void showError(@NonNull String message) {
        JOptionPane.showMessageDialog(
                mainFrame,
                message,
                UiConstants.errorDialogTitle,
                JOptionPane.ERROR_MESSAGE,
                appIcon);
    }

    /**
     * Shows an info dialog with the provided message.
     *
     * @param message The info message to display
     */
    public void showInfo(@NonNull String message) {
        JOptionPane.showMessageDialog(
                mainFrame,
                message,
                Constants.programName,
                JOptionPane.INFORMATION_MESSAGE,
                appIcon);
    }

    /**
     * Shows an input dialog with the provided message.
     *
     * @param message The input prompt message
     * @return The user's input string, or null if cancelled
     */
    public String showInput(@NonNull String message) {
        Object input =
                JOptionPane.showInputDialog(
                        mainFrame,
                        message,
                        Constants.programName,
                        JOptionPane.QUESTION_MESSAGE,
                        appIcon,
                        null,
                        "");

        if (input instanceof String string) {
            return string;
        } else {
            return null;
        }
    }

    /**
     * Shows a confirmation dialog with the provided message.
     *
     * @param message The confirmation message
     * @return true if user clicked Yes, false otherwise
     */
    public boolean showConfirm(@NonNull String message) {
        int response =
                JOptionPane.showConfirmDialog(
                        mainFrame,
                        message,
                        UiConstants.yesNoDialogTitle,
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE,
                        appIcon);

        return response == JOptionPane.YES_OPTION;
    }

    /**
     * Shows a confirmation dialog with a "Don't show again" checkbox.
     *
     * @param message The confirmation message
     * @return ConfirmationResult containing the user's choice and checkbox state
     */
    public ConfirmationResult showConfirmWithDontShowAgain(@NonNull String message) {
        JCheckBox checkbox = new JCheckBox(UiConstants.dontShowAgainString);
        Object[] params = {message, checkbox};

        int response =
                JOptionPane.showConfirmDialog(
                        mainFrame,
                        params,
                        UiConstants.yesNoDialogTitle,
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE,
                        appIcon);

        boolean confirmed = response == JOptionPane.YES_OPTION;
        boolean dontShowAgain = checkbox.isSelected();

        return new ConfirmationResult(confirmed, dontShowAgain);
    }

    /**
     * Shows a file dialog for opening files.
     *
     * @param title The dialog title
     * @param initialDirectory The initial directory to show
     * @param fileFilter Optional file filter
     * @return The selected file, or null if cancelled
     */
    public File showFileOpenDialog(
            @NonNull String title, @NonNull String initialDirectory, FilenameFilter fileFilter) {
        FileDialog fd = new FileDialog(mainFrame, title, FileDialog.LOAD);
        fd.setDirectory(initialDirectory);

        if (fileFilter != null) {
            fd.setFilenameFilter(fileFilter);
        }

        fd.setVisible(true);

        String dir = fd.getDirectory();
        String file = fd.getFile();

        if (dir != null && file != null) {
            return new File(dir + file);
        }

        return null;
    }

    /**
     * Shows a JFileChooser dialog for opening files or directories.
     *
     * @param title The dialog title
     * @param initialDirectory The initial directory to show
     * @param selectionMode The file selection mode
     * @param fileFilter Optional file filter
     * @return The selected file, or null if cancelled
     */
    public File showFileChooser(
            @NonNull String title,
            @NonNull String initialDirectory,
            int selectionMode,
            javax.swing.filechooser.FileFilter fileFilter) {
        JFileChooser jfc = new JFileChooser(initialDirectory);
        jfc.setDialogTitle(title);
        jfc.setFileSelectionMode(selectionMode);

        if (fileFilter != null) {
            jfc.setFileFilter(fileFilter);
        }

        int result = jfc.showOpenDialog(mainFrame);

        if (result == JFileChooser.APPROVE_OPTION) {
            return jfc.getSelectedFile();
        }

        return null;
    }

    /** Result of a confirmation dialog with "Don't show again" checkbox. */
    public static class ConfirmationResult {
        private final boolean confirmed;
        private final boolean dontShowAgain;

        public ConfirmationResult(boolean confirmed, boolean dontShowAgain) {
            this.confirmed = confirmed;
            this.dontShowAgain = dontShowAgain;
        }

        public boolean isConfirmed() {
            return confirmed;
        }

        public boolean isDontShowAgain() {
            return dontShowAgain;
        }
    }
}
