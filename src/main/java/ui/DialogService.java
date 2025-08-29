package ui;

import env.ProgramName;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.File;
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

    /** Title of all yes/no dialogs in the program. */
    private static final String YES_NO_DIALOG_TITLE = "Select an Option";

    /** Standard String asking whether or not user would like to see similar dialogs again. */
    private static final String DONT_SHOW_AGAIN_STRING = "Do not show this message again.";

    /** Title of all error dialogs in the program. */
    private static final String ERROR_DIALOG_TITLE = "Error";

    private final MainFrame mainFrame;
    private final ImageIcon appIcon;
    private final ProgramName programName;

    @Inject
    public DialogService(@NonNull MainFrame mainFrame, @NonNull ProgramName programName) {
        this.mainFrame = mainFrame;
        this.programName = programName;
        this.appIcon = new ImageIcon(DialogService.class.getResource("/images/headphones48.png"));
    }

    /**
     * Shows an error dialog with the provided message.
     *
     * @param message The error message to display
     */
    public void showError(@NonNull String message) {
        JOptionPane.showMessageDialog(
                mainFrame, message, ERROR_DIALOG_TITLE, JOptionPane.ERROR_MESSAGE, appIcon);
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
                programName.toString(),
                JOptionPane.INFORMATION_MESSAGE,
                appIcon);
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
                        YES_NO_DIALOG_TITLE,
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
        JCheckBox checkbox = new JCheckBox(DONT_SHOW_AGAIN_STRING);
        Object[] params = {message, checkbox};

        int response =
                JOptionPane.showConfirmDialog(
                        mainFrame,
                        params,
                        YES_NO_DIALOG_TITLE,
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE,
                        appIcon);

        boolean confirmed = response == JOptionPane.YES_OPTION;
        boolean dontShowAgain = checkbox.isSelected();

        return new ConfirmationResult(confirmed, dontShowAgain);
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
